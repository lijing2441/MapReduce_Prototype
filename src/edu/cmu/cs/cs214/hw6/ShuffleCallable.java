package edu.cmu.cs.cs214.hw6;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import edu.cmu.cs.cs214.hw6.util.KeyValuePair;

public class ShuffleCallable<V> implements Callable<V> {

	private final WorkerInfo mWorker;
	private final WorkerInfo sendingWorker;
	private ConcurrentHashMap<String, Iterator<String>> hm;

	public ShuffleCallable(WorkerInfo mWorker, WorkerInfo sendingWorker,
			ConcurrentHashMap<String, Iterator<String>> hm) {
		super();
		this.hm = hm;
		this.mWorker = mWorker;
		this.sendingWorker = sendingWorker;
	}

	public WorkerInfo getWorker() {
		return mWorker;
	}

	@Override
	public V call() throws Exception {
		// TODO Auto-generated method stub
		Socket socket = null;
		try {
			socket = new Socket(mWorker.getHost(), mWorker.getPort());
			ObjectOutputStream out = new ObjectOutputStream(
					socket.getOutputStream());
			out.writeObject(new ExecuteShuffleTaskCommand<Integer>(mWorker,
					sendingWorker));
			ObjectInputStream in = new ObjectInputStream(
					socket.getInputStream());
			ConcurrentHashMap<String, ArrayList<String>> hmap = new ConcurrentHashMap<String, ArrayList<String>>();
			Object o = new Object();
			while ((o = in.readObject()) != null) {
				KeyValuePair kvp = (KeyValuePair) o;
				String key = kvp.getKey();
				String value = kvp.getValue();

				if (hmap.containsKey(key)) {
					hmap.get(key).add(value);
				} else {
					ArrayList<String> arr = new ArrayList<String>();
					arr.add(value);
					hmap.put(key, arr);
				}
			}
			for (String s : hmap.keySet()) {
				hm.put(s, hmap.get(s).iterator());
			}
			Integer i = new Integer(1);
			return (V) i;
		} finally {
			try {
				if (socket != null) {
					socket.close();
				}
			} catch (IOException e) {
				// Ignore because we're about to exit anyway.
			}
		}
	}

}
