// Copyright (C) 2014 by Xiao Shaun Wang <wangxiao@cs.umd.edu>
package test;

import org.junit.Test;

import scoram.TrivialPrivateOram;
import flexsc.CompEnv;
import flexsc.Mode;
import flexsc.Party;
import gc.GCSignal;


public class TestTrivialOram {
	final int N = 1<<5;
	int writecount = N;
	int readcount = N*2;
	int dataSize = 32;
	public TestTrivialOram() {
	}
	
	class GenRunnable extends network.Server implements Runnable {
		int port;
		GenRunnable (int port) {
			this.port = port;
		}

		public void run() {
			try {
				listen(port);

				int data[] = new int[N+1];
				@SuppressWarnings("unchecked")
				CompEnv<GCSignal> gen = CompEnv.getEnv(Mode.REAL, Party.Alice, is, os);
				TrivialPrivateOram<GCSignal> client = new TrivialPrivateOram<GCSignal>(gen, N, dataSize);
				System.out.println("logN:"+client.logN+", N:"+client.N);
				
				
				for(int i = 0; i < writecount; ++i) {
					int element = i%N;
					data[element] = 2*element-11;
					long t1 = System.currentTimeMillis();
					GCSignal[] scData = client.env.inputOfAlice(Utils.fromInt(data[element], 32));
					client.write(client.lib.toSignals(element), scData);
					long t2 = System.currentTimeMillis() - t1;
					System.out.println("time: "+t2/1000.0);
					System.gc();
					Runtime rt = Runtime.getRuntime(); 
				    double usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024.0 / 1024.0;
				    System.out.println("mem: "+usedMB);
				}

				for(int i = 0; i < readcount; ++i){
					int element = i%N;

					GCSignal[] scb = client.read(client.lib.toSignals(element));
					boolean[] b = client.env.outputToAlice(client.lib.add(scb,  scb));
					if(Utils.toInt(b) != data[element]*2) {
						System.out.println("inconsistent: "+element+" "+Utils.toInt(b) + " "+data[element]);
					}
				}
	
				os.flush();

				disconnect();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	class EvaRunnable extends network.Client implements Runnable {
		String host;
		int port;

		EvaRunnable (String host, int port) {
			this.host =  host;
			this.port = port;
		}

		public void run() {
			try {
				connect(host, port);			
				
				@SuppressWarnings("unchecked")
				CompEnv<GCSignal> eva = CompEnv.getEnv(Mode.REAL, Party.Bob, is, os);
				TrivialPrivateOram<GCSignal> server = new TrivialPrivateOram<GCSignal>(eva, N, dataSize);
				
				
				for(int i = 0; i < writecount; ++i) {
					int element = i%N;
					GCSignal[] scData = server.env.inputOfAlice(new boolean[32]);
					server.write(server.lib.toSignals(element), scData);
				}

				for(int i = 0; i < readcount; ++i){
					int element = i%N;
					GCSignal[] scb = server.read(server.lib.toSignals(element));
					server.env.outputToAlice(server.lib.add(scb,  scb));
				}
				
				os.flush();

				disconnect();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
	
	@Test
	public void runThreads() throws Exception {
		GenRunnable gen = new GenRunnable(12345);
		EvaRunnable eva = new EvaRunnable("localhost", 12345);
		Thread tGen = new Thread(gen);
		Thread tEva = new Thread(eva);
		tGen.start(); Thread.sleep(10);
		tEva.start();
		tGen.join();
	}
}