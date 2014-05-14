package edu.cmu.cs.cs214.hw6;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.Scanner;

import edu.cmu.cs.cs214.hw6.util.KeyValuePair;
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
public class ExecuteShuffleTaskCommand<T extends Serializable> extends
		WorkerCommand {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6095828885846985099L;
	private static final String TAG = "ExecuteShuffleCommand";
	private final WorkerInfo mWorker;
	private final WorkerInfo sendingWorker;

	public ExecuteShuffleTaskCommand(WorkerInfo worker, WorkerInfo sendingWorker) {
		mWorker = worker;
		this.sendingWorker = sendingWorker;
	}

	@Override
	public void run() {
		Socket socket = getSocket();
		ObjectOutputStream out = null;
		FileInputStream fis;
		try {
			out = new ObjectOutputStream(socket.getOutputStream());
			String workerName = sendingWorker.getName();
			int workerNum = workerName.charAt(workerName.length() - 1) - '0';
			File file = new File(
					WorkerStorage.getIntermediateResultsDirectory(mWorker
							.getName()) + "\\" + "immediate.txt");
			if (!file.exists()) {
				out.writeObject(null);
				out.close();
				return;
			}
			Scanner sc = new Scanner(file);
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				String[] words = line.split("\\W+");
				if (words.length < 2)
					continue;
				String key = words[0];
				String value = words[1];
				if ((Math.abs(key.hashCode()) % 4) + 1 == workerNum) {
					KeyValuePair kvp = new KeyValuePair(key, value);
					out.writeObject(kvp);
				}
			}
			sc.close();
			out.writeObject(null);
			out.close();
		} catch (IOException e) {
			Log.e(TAG, "I/O error while shuffling.", e);
			try {
				out = new ObjectOutputStream(socket.getOutputStream());
				out.writeObject(0);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
}
