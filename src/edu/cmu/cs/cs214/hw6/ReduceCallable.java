package edu.cmu.cs.cs214.hw6;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Callable;

public class ReduceCallable<V> implements Callable<V> {

	private final ReduceTask mTask;
    private final WorkerInfo mWorker;
    private final List<WorkerInfo> mWorkers;
    
	public ReduceCallable(ReduceTask mTask, WorkerInfo mWorker,List<WorkerInfo> mWorkers) {
		super();
		this.mTask = mTask;
		this.mWorker = mWorker;
		this.mWorkers=mWorkers;
	}

	public WorkerInfo getWorker() {
		return mWorker;
	}

	@Override
	public V call() throws Exception {
		// TODO Auto-generated method stub
		Socket socket = null;
        try {
            // Establish a connection with the worker server.
            socket = new Socket(mWorker.getHost(), mWorker.getPort());

            // Create the ObjectOutputStream and write the WorkerCommand
            // over the network to be read and executed by a WorkerServer.
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(new ExecuteReduceTaskCommand<Integer>(mTask, mWorker,mWorkers));

            // Note that we instantiate the ObjectInputStream AFTER writing
            // the object over the objectOutputStream. Initializing it
            // immediately after initializing the ObjectOutputStream (but
            // before writing the object) will cause the entire program to
            // block, as described in this StackOverflow answer:
            // http://stackoverflow.com/q/5658089/844882:
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            // Read and return the worker's final result.
            return (V) in.readObject();
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
