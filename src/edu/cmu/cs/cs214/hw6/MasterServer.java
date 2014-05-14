package edu.cmu.cs.cs214.hw6;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import edu.cmu.cs.cs214.hw6.util.Log;
import edu.cmu.cs.cs214.hw6.util.StaffUtils;

/**
 * This class represents the "master server" in the distributed map/reduce
 * framework. The {@link MasterServer} is in charge of managing the entire
 * map/reduce computation from beginning to end. The {@link MasterServer}
 * listens for incoming client connections on a distinct host/port address, and
 * is passed an array of {@link WorkerInfo} objects when it is first initialized
 * that provides it with necessary information about each of the available
 * workers in the system (i.e. each worker's name, host address, port number,
 * and the set of {@link Partition}s it stores). A single map/reduce computation
 * managed by the {@link MasterServer} will typically behave as follows:
 * 
 * <ol>
 * <li>Wait for the client to submit a map/reduce task.</li>
 * <li>Distribute the {@link MapTask} across a set of "map-workers" and wait for
 * all map-workers to complete.</li>
 * <li>Distribute the {@link ReduceTask} across a set of "reduce-workers" and
 * wait for all reduce-workers to complete.</li>
 * <li>Write the final key/value pair results of the computation back to the
 * client.</li>
 * </ol>
 */
public class MasterServer extends Thread {
	private final int mPort;
	private final List<WorkerInfo> mWorkers;

	private static final int MAX_POOL_SIZE = Runtime.getRuntime()
			.availableProcessors();
	private static final String TAG = "Master Server";
	private final ExecutorService mExecutor;

	/**
	 * The {@link MasterServer} constructor.
	 * 
	 * @param masterPort
	 *            The port to listen on.
	 * @param workers
	 *            Information about each of the available workers in the system.
	 */
	public MasterServer(int masterPort, List<WorkerInfo> workers) {
		mPort = masterPort;
		mWorkers = workers;
		int numThreads = Math.min(MAX_POOL_SIZE, workers.size());
		mExecutor = Executors.newFixedThreadPool(numThreads);
	}

	@Override
	public void run() {
		ServerSocket s = null;
		Socket socket = null;
		MapTask mapTask = null;
		ReduceTask reduceTask = null;
		List<Future<Integer>> results = null;
		List<Future<String>> results2 = null;
		List<MapCallable<Integer>> callables = new ArrayList<MapCallable<Integer>>();
		List<ReduceCallable<String>> callables2 = new ArrayList<ReduceCallable<String>>();
		boolean flag = true;
		boolean flag2 = true;
		List<String> finalResults = new ArrayList<String>();
		try {
			s = new ServerSocket(mPort);
		} catch (IOException e) {
			Log.e(TAG, "Could not open server socket on port " + mPort + ".", e);
			return;
		}
		Log.i(TAG, "Listening for incoming commands on port " + mPort + ".");
		while (true) {
			try {
				socket = s.accept();
				ObjectInputStream ois = new ObjectInputStream(
						socket.getInputStream());
				mapTask = (MapTask) ois.readObject();
				reduceTask = (ReduceTask) ois.readObject();
				Log.i(TAG, "Map phase starts");
				// decide which worker to do which part of the map work here
				// and pass the partition to a certain worker.
				HashMap<String, ArrayList<WorkerInfo>> hm = new HashMap<String, ArrayList<WorkerInfo>>();
				for (WorkerInfo worker : mWorkers) {
					for (Partition p : worker.getPartitions()) {
						if (hm.containsKey(p.getPartitionName())) {
							ArrayList<WorkerInfo> arr = hm.get(p
									.getPartitionName());
							arr.add(worker);
						} else {
							ArrayList<WorkerInfo> arr = new ArrayList<WorkerInfo>();
							arr.add(worker);
							hm.put(p.getPartitionName(), arr);
						}
					}
				}

				for (String str : hm.keySet()) {
					ArrayList<WorkerInfo> arr = hm.get(str);
					int randomNum = new Random().nextInt(arr.size());
					for (Partition p : arr.get(randomNum).getPartitions()) {
						if (p.getPartitionName().equals(str)) {
							arr.get(randomNum).getExecuetePartitions().add(p);
							break;
						}
					}
				}

				for (WorkerInfo worker : mWorkers) {
					callables.add(new MapCallable<Integer>(mapTask, worker));
				}
				while (flag2) {
					while (flag) {
						try {
							results = mExecutor.invokeAll(callables);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}

						// At this point we know that all of the callable tasks
						// have
						// finished executing, so now we can sum up all of the
						// results.
						int totalCount = 0;
						for (int i = 0; i < results.size(); i++) {
							MapCallable<Integer> callable = callables.get(i);
							Future<Integer> result = results.get(i);
							try {
								totalCount += result.get();
								// if all have result, stop the while loop
								if (i == results.size() - 1)
									flag = false;
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							} catch (ExecutionException e) {
								String workerHost = callable.getWorker()
										.getHost();
								int workerPort = callable.getWorker().getPort();
								String info = String.format(
										"[host=%s, port=%d]", workerHost,
										workerPort);
								Log.e(TAG,
										"Warning! Failed to execute map task for worker: "
												+ info, e.getCause());

								mWorkers.remove(callable.getWorker());
								callables.clear();
								for (Partition p : callable.getWorker()
										.getExecuetePartitions()) {
									for (WorkerInfo wi : hm.get(p)) {
										if (!wi.getName().equals(
												callable.getWorker().getName())) {
											wi.getExecuetePartitions().add(p);
											break;
										}
									}
								}
								for (WorkerInfo worker : mWorkers) {
									callables.add(new MapCallable<Integer>(
											mapTask, worker));
								}
							}
						}
					}

					Log.i(TAG, "Map phase stops");
					Log.i(TAG, "Reduce phase starts");

					for (WorkerInfo worker : mWorkers) {
						callables2.add(new ReduceCallable<String>(reduceTask,
								worker, mWorkers));
					}
					try {
						results2 = mExecutor.invokeAll(callables2);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					for (int i = 0; i < results2.size(); i++) {
						ReduceCallable<String> callable2 = callables2.get(i);
						Future<String> finalResult = results2.get(i);
						try {
							finalResults.add(finalResult.get());
							if (i == results2.size() - 1) {
								flag2 = false;
							}
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						} catch (ExecutionException e) {
							String workerHost = callable2.getWorker().getHost();
							int workerPort = callable2.getWorker().getPort();
							String info = String.format("[host=%s, port=%d]",
									workerHost, workerPort);
							Log.e(TAG,
									"Warning! Failed to execute reduce task for worker: "
											+ info, e.getCause());
							mWorkers.remove(callable2.getWorker());
							callables2.clear();
							callables.clear();
							for (Partition p : callable2.getWorker()
									.getExecuetePartitions()) {
								for (WorkerInfo wi : hm.get(p)) {
									if (!wi.getName().equals(
											callable2.getWorker().getName())) {
										wi.getExecuetePartitions().add(p);
										break;
									}
								}
							}
							for (WorkerInfo worker : mWorkers) {
								callables.add(new MapCallable<Integer>(mapTask,
										worker));
							}
						}
					}
				}

				Log.i(TAG, "Reduce phase stops");
				Socket clientSocket = new Socket("localhost", 8888);
				ObjectOutputStream oos = new ObjectOutputStream(
						clientSocket.getOutputStream());
				oos.writeObject(finalResults);
				System.out
						.println("The final result has already been sended to client");
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
				// Don't forget to close your ExecutorService when you are
				// finished
				// with it! Otherwise the JVM will never kill the process
				// associated
				// with this program (since it will think that there are still
				// threads being executed inside).
				mExecutor.shutdown();
			}
		}
	}

	/********************************************************************/
	/***************** STAFF CODE BELOW. DO NOT MODIFY. *****************/
	/********************************************************************/

	/**
	 * Starts the master server on a distinct port. Information about each
	 * available worker in the distributed system is parsed and passed as an
	 * argument to the {@link MasterServer} constructor. This information can be
	 * either specified via command line arguments or via system properties
	 * specified in the <code>master.properties</code> and
	 * <code>workers.properties</code> file (if no command line arguments are
	 * specified).
	 */
	public static void main(String[] args) {
		StaffUtils.makeMasterServer(args).start();
	}

}
