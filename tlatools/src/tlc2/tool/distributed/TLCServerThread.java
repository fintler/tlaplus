// Copyright (c) 2003 Compaq Corporation.  All rights reserved.
// Portions Copyright (c) 2003 Microsoft Corporation.  All rights reserved.
// Last modified on Mon 30 Apr 2007 at 13:18:34 PST by lamport  
//      modified on Fri Mar  2 23:46:22 PST 2001 by yuanyu   

package tlc2.tool.distributed;

import java.io.EOFException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import tlc2.TLCGlobals;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.TLCState;
import tlc2.tool.TLCStateVec;
import tlc2.tool.WorkerException;
import tlc2.tool.distributed.selector.IBlockSelector;
import tlc2.tool.queue.StateQueue;
import tlc2.util.BitVector;
import tlc2.util.IdThread;
import tlc2.util.LongVec;

public class TLCServerThread extends IdThread {
	/**
	 * Identifies the worker
	 */
	private int receivedStates, sentStates;
	private final CyclicBarrier barrier;
	private final IBlockSelector selector;
	private final Timer keepAliveTimer;

	public TLCServerThread(int id, TLCWorkerRMI worker, TLCServer tlc) {
		this(id, worker, tlc, null, null);
	}
	
	public TLCServerThread(int id, TLCWorkerRMI worker, TLCServer tlc, CyclicBarrier aBarrier, IBlockSelector aSelector) {
		super(id);
		this.setWorker(worker);
		this.tlcServer = tlc;
		this.barrier = aBarrier;
		this.selector = aSelector;
		
		// schedule a timer to periodically (60s) check server aliveness 
		keepAliveTimer = new Timer("TLCWorker KeepAlive Timer [" + uri.toASCIIString() + "]", true);
		keepAliveTimer.schedule(new TLCTimerTask(), 10000, 60000);
	}

	private TLCWorkerRMI worker;
	private TLCServer tlcServer;
	private URI uri;
	private long lastInvocation;
	
	/**
	 * Current unit of work or null
	 */
	private TLCState[] states = new TLCState[0];

	public final TLCWorkerRMI getWorker() {
		return this.worker;
	}

	public final void setWorker(TLCWorkerRMI worker) {
		this.worker = new TLCWorkerSmartProxy(worker);
		try {
			this.uri = worker.getURI();
		} catch (RemoteException e) {
			//TODO handle more gracefully
			e.printStackTrace();
		}
		// update thread name
		setName("TLCServerThread-[" + uri.toASCIIString() + "]");
	}

	/**
	 * This method gets a state from the queue, generates all the possible next
	 * states of the state, checks the invariants, and updates the state set and
	 * state queue.
	 */
	public void run() {
		waitOnBarrier();
		
		TLCGlobals.incNumWorkers(1);
		TLCStateVec[] newStates = null;
		LongVec[] newFps = null;

		final StateQueue stateQueue = this.tlcServer.stateQueue;
		try {
			START: while (true) {
				// blocks until more states available or all work is done
				states = selector.getBlocks(stateQueue, worker);
				if (states == null) {
					synchronized (this.tlcServer) {
						this.tlcServer.setDone();
						this.tlcServer.notify();
					}
					stateQueue.finishAll();
					return;
				}
				
				// without initial states no need to bother workers
				if (states.length == 0) {
					continue;
				}
				
				// count statistics
				sentStates += states.length;

				// real work happens here:
				// worker computes next states for states
				boolean workDone = false;
				while (!workDone) {
					try {
						final Object[] res = this.worker.getNextStates(states);
						newStates = (TLCStateVec[]) res[0];
						receivedStates += newStates[0].size();
						newFps = (LongVec[]) res[1];
						workDone = true;
						lastInvocation = System.currentTimeMillis();
					} catch (RemoteException e) {
						// non recoverable errors
						final Throwable cause = e.getCause();
						if (cause instanceof EOFException && cause.getMessage() == null) {
							MP.printMessage(EC.TLC_DISTRIBUTED_EXCEED_BLOCKSIZE, Integer.toString(states.length / 2));
							// states[] exceeds maximum transferable size
							// (add states back to queue and retry)
							stateQueue.sEnqueue(states);
							// half the maximum size and use it as a limit from now on
							selector.setMaxTXSize(states.length / 2);
							// go back to beginning
							continue START;
						} else {
							MP.printMessage(EC.TLC_DISTRIBUTED_WORKER_LOST, throwableToString(e));
							handleRemoteWorkerLost(stateQueue);
							return;
						}
					} catch (NullPointerException e) {
						MP.printMessage(EC.TLC_DISTRIBUTED_WORKER_LOST, throwableToString(e));
						handleRemoteWorkerLost(stateQueue);
						return;
					}
				}

				// add fingerprints to fingerprint manager (delegates to
				// corresponding fingerprint server)
				// (Why isn't this done by workers directly?
				// -> because if the worker crashes while computing states, the
				// fp set would be inconsistent => making it an "atomic" operation)
				BitVector[] visited = this.tlcServer.fpSetManager
						.putBlock(newFps);

				// recreate newly computed states and add them to queue
				for (int i = 0; i < visited.length; i++) {
					BitVector.Iter iter = new BitVector.Iter(visited[i]);
					int index;
					while ((index = iter.next()) != -1) {
						TLCState state = newStates[i].elementAt(index);
						// write state id and state fp to .st file for
						// checkpointing
						long fp = newFps[i].elementAt(index);
						state.uid = this.tlcServer.trace.writeState(state,
								fp);
						// add state to state queue for further processing
						stateQueue.sEnqueue(state);
					}
				}
			}
		} catch (Throwable e) {
			TLCState state1 = null, state2 = null;
			if (e instanceof WorkerException) {
				state1 = ((WorkerException) e).state1;
				state2 = ((WorkerException) e).state2;
			}
			if (this.tlcServer.setErrState(state1, true)) {
				MP.printError(EC.GENERAL, e);
				if (state1 != null) {
					try {
						this.tlcServer.trace.printTrace(state1,
								state2);
					} catch (Exception e1) {
						MP.printError(EC.GENERAL, e1);
					}
				}
				stateQueue.finishAll();
				synchronized (this.tlcServer) {
					this.tlcServer.notify();
				}
			}
		}
	}

	private String throwableToString(final Exception e) {
		final Writer result = new StringWriter();
	    final PrintWriter printWriter = new PrintWriter(result);
	    e.printStackTrace(printWriter);
	    return result.toString();
	}

	/**
	 * Handles the case of a disconnected remote worker
	 * @param stateQueue
	 */
	private void handleRemoteWorkerLost(final StateQueue stateQueue) {
		keepAliveTimer.cancel();
		tlcServer.removeTLCServerThread(this);
		stateQueue.sEnqueue(states != null ? states : new TLCState[0]);
		TLCGlobals.incNumWorkers(-1);
		MP.printMessage(EC.TLC_DISTRIBUTED_WORKER_DEREGISTERED, getUri().toString());
	}

	/**
	 * Causes this thread to wait for all other worker threads before it starts
	 * computing states. The barrier may be null in which case threads start
	 * computing next states immediately after creation.
	 */
	private void waitOnBarrier() {
		try {
			if(barrier != null)
				barrier.await();
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		} catch (BrokenBarrierException e2) {
			e2.printStackTrace();
		}
	}

	/**
	 * @return The current amount of states the corresponding worker is
	 *         computing on
	 */
	public int getCurrentSize() {
		return states.length;
	}

	/**
	 * @return the url
	 */
	public URI getUri() {
		return this.uri;
	}
	
	/**
	 * @return the receivedStates
	 */
	public int getReceivedStates() {
		return receivedStates;
	}

	/**
	 * @return the sentStates
	 */
	public int getSentStates() {
		return sentStates;
	}
	
	//************************************//
	
	private class TLCTimerTask extends TimerTask {

		/* (non-Javadoc)
		 * @see java.util.TimerTask#run()
		 */
		public void run() {
			long now = new Date().getTime();
			if(lastInvocation == 0 || (now - lastInvocation) > 60000) {
				try {
					if(!worker.isAlive()) {
						handleRemoteWorkerLost(tlcServer.stateQueue);
					}
				} catch (RemoteException e) {
					handleRemoteWorkerLost(tlcServer.stateQueue);
				}
			}
		}
	}
}
