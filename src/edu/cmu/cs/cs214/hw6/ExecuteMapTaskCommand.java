package edu.cmu.cs.cs214.hw6;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.List;
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
public class ExecuteMapTaskCommand<T extends Serializable> extends
		WorkerCommand {
	private static final long serialVersionUID = -5314076333098679665L;
	private static final String TAG = "ExecuteTaskCommand";
	private final WorkerInfo mWorker;
	private final MapTask mTask;
	private final List<Partition> executePartitions;

	public ExecuteMapTaskCommand(MapTask task, WorkerInfo worker,
			List<Partition> list) {
		mTask = task;
		executePartitions = list;
		mWorker = worker;
	}

	@Override
	public void run() {
		Socket socket = getSocket();
		ObjectOutputStream out = null;
		String immediateFileName = WorkerStorage
				.getIntermediateResultsDirectory(mWorker.getName())
				+ "\\"
				+ "immediate.txt";
		File immediatefile = new File(immediateFileName);
		if (immediatefile.exists()) {
			immediatefile.delete();
		}
		PrintEmitter emitter = null;
		try {
			emitter = new PrintEmitter(immediateFileName);
		} catch (FileNotFoundException e2) {
			e2.printStackTrace();
		}
		try {
			// Opens a FileInputStream for the specified file, execute the task,
			// and close the input stream once we've calculated the result.
			FileInputStream in = null;
			for (Partition p : executePartitions) {
				for (File file : p) {
					in = new FileInputStream(
							WorkerStorage.getDataDirectory(mWorker.getName())
									+ "\\" + p.getPartitionName() + "\\"
									+ file.getName());
					mTask.execute(in, emitter);
					in.close();
				}
			}
			// Open an ObjectOutputStream to use to communicate with the client
			// that sent this command, and write the result back to the client.
			// use 1 to represent success
			out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(1);
		} catch (IOException e) {
			Log.e(TAG, "I/O error while executing task.", e);
		}
	}
}
