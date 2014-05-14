package edu.cmu.cs.cs214.hw6;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class PrintEmitter implements Emitter {

	private String  fileName;
	public PrintEmitter(String fileName) throws FileNotFoundException {
		this.fileName=fileName;
	}
	@Override
	public void emit(String key, String value) throws IOException {
		FileOutputStream fos=new FileOutputStream(fileName,true);
		PrintWriter pw=new PrintWriter(fos);
		pw.println(key+ " " +value);
		pw.close();
		fos.close();
	}
}
