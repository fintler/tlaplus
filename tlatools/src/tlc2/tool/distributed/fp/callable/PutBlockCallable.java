// Copyright (c) 2012 Markus Alexander Kuppe. All rights reserved.
package tlc2.tool.distributed.fp.callable;

import java.util.List;

import tlc2.tool.distributed.fp.FPSetManager;
import tlc2.tool.distributed.fp.FPSetManager.FPSets;
import tlc2.util.BitVector;
import tlc2.util.Fingerprint;

public class PutBlockCallable extends FPSetManagerCallable {
	
	public PutBlockCallable(FPSetManager fpSetManager, List<FPSets> fpSets, List<List<Fingerprint>> fps, int index) {
		super(fpSetManager, fpSets, fps, index);
	}
	
	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	public BitVectorWrapper call() throws Exception {
		try {
			BitVector bv = fpset.get(index).putBlock(fps.get(index));
			return new BitVectorWrapper(index, bv);
		} catch (Exception e) {
			return reassign(e);
		}
	}
}