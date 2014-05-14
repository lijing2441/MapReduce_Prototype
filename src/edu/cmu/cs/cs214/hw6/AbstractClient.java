package edu.cmu.cs.cs214.hw6;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import edu.cmu.cs.cs214.hw6.plugin.wordcount.WordCountClient;
import edu.cmu.cs.cs214.hw6.plugin.wordprefix.WordPrefixClient;

/**
 * An abstract client class used primarily for code reuse between the
 * {@link WordCountClient} and {@link WordPrefixClient}.
 */
public abstract class AbstractClient {
	private final String mMasterHost;
	private final int mMasterPort;
	private Socket socket;
	private ObjectOutputStream o;
	private ServerSocket ss;

	/**
	 * The {@link AbstractClient} constructor.
	 * 
	 * @param masterHost
	 *            The host name of the {@link MasterServer}.
	 * @param masterPort
	 *            The port that the {@link MasterServer} is listening on.
	 */
	public AbstractClient(String masterHost, int masterPort) {
		mMasterHost = masterHost;
		mMasterPort = masterPort;
	}

	protected abstract MapTask getMapTask();

	protected abstract ReduceTask getReduceTask();

	public void execute() {
		final MapTask mapTask = getMapTask();
		final ReduceTask reduceTask = getReduceTask();

		// TODO: Submit the map/reduce task to the master and wait for the task
		// to complete.
		try {
			socket = new Socket(mMasterHost, mMasterPort);
			o = new ObjectOutputStream(socket.getOutputStream());
			o.writeObject(mapTask);
			o.writeObject(reduceTask);
			o.flush();
			socket.close();
			ss = new ServerSocket(8888);
			Socket s = ss.accept();
			ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
			List<String> l = (List<String>) ois.readObject();
			for (String str : l) {
				System.out.println(str);
			}
			s.close();
			ss.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
