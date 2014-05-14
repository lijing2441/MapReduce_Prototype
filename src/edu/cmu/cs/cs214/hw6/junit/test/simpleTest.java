package edu.cmu.cs.cs214.hw6.junit.test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.junit.Test;
import edu.cmu.cs.cs214.hw6.util.KeyValuePair;
import edu.cmu.cs.cs214.hw6.util.WorkerStorage;

public class simpleTest {

	@Test
	public void emit() throws IOException {
		FileOutputStream fos = new FileOutputStream(
				WorkerStorage.getIntermediateResultsDirectory("worker1") + "\\"
						+ "1.txt");
		ObjectOutputStream out = new ObjectOutputStream(fos);
		KeyValuePair kvp = new KeyValuePair("attribute", "1");
		out.writeObject(kvp);
		out.reset();
		out.writeObject(kvp);
	}

	@Test
	public void test2() {
		String str = WorkerStorage.getIntermediateResultsDirectory("worker1");
		System.out.println(str);
	}
}
