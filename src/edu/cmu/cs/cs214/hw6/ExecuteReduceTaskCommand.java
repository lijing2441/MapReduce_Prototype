package edu.cmu.cs.cs214.hw6;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import edu.cmu.cs.cs214.hw6.util.Log;
import edu.cmu.cs.cs214.hw6.util.WorkerStorage;

/**
 * A {@link WorkerCommand} that executes a {@link Task} and sends the calculated
 * result back to the client.
 * 
 * Note that the generic type <code>T</code> must extend {@link Serializable}
 * since we will be writing it over the network back to the client using an
 * {@link ObjectOutputStream}.
 */
public class ExecuteReduceTaskCommand<T extends Serializable> extends
		WorkerCommand {
	private static final long serialVersionUID = -5314076333098679665L;
	private static final String TAG = "ExecuteTaskCommand";
	private final WorkerInfo mWorker;
	private final ReduceTask mTask;
	private ConcurrentHashMap<String, Iterator<String>> hm;
	private int numThreads = 0;

	private static final int MAX_POOL_SIZE = Runtime.getRuntime()
			.availableProcessors();
	private final List<WorkerInfo> mWorkers;

	public ExecuteReduceTaskCommand(ReduceTask task, WorkerInfo worker,
			List<WorkerInfo> mWorkers) {
		mTask = task;
		mWorker = worker;
		hm = new ConcurrentHashMap<String, Iterator<String>>();
		numThreads = Math.min(MAX_POOL_SIZE, mWorkers.size());
		this.mWorkers = mWorkers;
	}

	@Override
	public void run() {
		ExecutorService mExecutor = Executors.newFixedThreadPool(numThreads);
		Socket socket = getSocket();
		ObjectOutputStream out = null;
		List<Future<Integer>> results = null;
		boolean flag = true;

		List<ShuffleCallable<Integer>> callables = new ArrayList<ShuffleCallable<Integer>>();
		for (WorkerInfo worker : mWorkers) {
			if (!worker.equals(mWorker)) {
				callables
						.add(new ShuffleCallable<Integer>(worker, mWorker, hm));
			}
		}

		try {
			results = mExecutor.invokeAll(callables);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		int total = 0;
		for (int i = 0; i < results.size(); i++) {
			ShuffleCallable<Integer> callable = callables.get(i);
			Future<Integer> finalResult = results.get(i);
			try {
				total += finalResult.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				String workerHost = callable.getWorker().getHost();
				int workerPort = callable.getWorker().getPort();
				String info = String.format("[host=%s, port=%d]", workerHost,
						workerPort);
				Log.e(TAG,
						"Warning! Failed to execute shuffle task for worker: "
								+ info, e.getCause());
				flag = false;
			}
		}
		mExecutor.shutdown();

		if (flag) {
			String finalFileName = WorkerStorage
					.getFinalResultsDirectory(mWorker.getName())
					+ "\\"
					+ "final.txt";
			File finalFile = new File(finalFileName);
			if (finalFile.exists()) {
				finalFile.delete();
			}
			try {
				for (String str : hm.keySet()) {
					mTask.execute(str, (Iterator<String>) hm.get(str),
							new PrintEmitter(finalFileName));
				}
				// Open an ObjectOutputStream to use to communicate with the
				// client
				// that sent this command, and write the result back to the
				// client.
				// use 1 to represent success
				out = new ObjectOutputStream(socket.getOutputStream());
				out.writeObject(finalFileName);
			} catch (IOException e) {
				Log.e(TAG, "I/O error while executing task.", e);
			}
		}
	}
}
