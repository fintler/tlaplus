// Copyright (c) 2003 Compaq Corporation.  All rights reserved.
// Last modified on Tue May 25 23:22:20 PDT 1999 by yuanyu
package tlc2.util;

import java.io.IOException;
import java.util.logging.Level;

import sun.misc.Unsafe;
import tlc2.tool.fp.FPSetConfiguration;

/**
 * A 128-bit fingerprint is stored in an instance of the type <code>long</code>.
 * The static methods of <code>FP64</code> are used to initialize 64-bit
 * fingerprints and to extend them.
 * 
 * Written by Allan Heydon and Marc Najork.
 */
@SuppressWarnings({ "restriction", "serial" })
public class FP128 extends GFFingerprint {

	public static class Factory extends FPFactory {
		public Factory(FPSetConfiguration fpConfig) {
			int fpIndex = fpConfig.getFPIndex();
			FP128.Init(fpIndex);
			LOGGER.log(Level.FINEST, "Instantiated FP128 factory with index {0}", fpIndex);
		}

		/* (non-Javadoc)
		 * @see tlc2.util.Fingerprint.FPFactory#newFingerprint()
		 */
		public Fingerprint newFingerprint() {
			return new FP128();
		}

		public Fingerprint newFingerprint(sun.misc.Unsafe unsafe, long posLower, long posHigher) {
			long lower = unsafe.getAddress(posLower);
			long higher = unsafe.getAddress(posHigher);
			if (lower == 0L && higher == 0L) {
				return null;
			} else {
				return new FP128(lower, higher);
			}
		}
		
		/* (non-Javadoc)
		 * @see tlc2.util.Fingerprint.FPFactory#newFingerprint(tlc2.util.BufferedRandomAccessFile)
		 */
		public Fingerprint newFingerprint(java.io.RandomAccessFile raf) throws IOException {
			return new FP128().read(raf);
		}
	}

	/* (non-Javadoc)
	 * @see tlc2.util.Fingerprint#extend(java.lang.String)
	 */
	public Fingerprint extend(String s) {
		
		final int mask = 0xFF;
		final int len = s.length();
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			IrredPolyLower = ((IrredPolyLower >>> 8) ^ (ByteModTable_7Lower[(((int) c) ^ ((int) IrredPolyLower))
					& mask]));
			IrredPolyHigher = ((IrredPolyHigher >>> 8) ^ (ByteModTable_7Higher[(((int) c) ^ ((int) IrredPolyHigher))
					& mask]));
		}
		
		return this;
	}

	/* (non-Javadoc)
	 * @see tlc2.util.Fingerprint#extend(byte[])
	 */
	public Fingerprint extend(byte[] bytes) {
		return extend(bytes, 0, bytes.length);
	}
	
	/* (non-Javadoc)
	 * @see tlc2.util.Fingerprint#extend(byte[], int, int)
	 */
	public Fingerprint extend(byte[] bytes, int start, int len) {

		int end = start + len;
		for (int i = start; i < end; i++) {
			IrredPolyLower = (IrredPolyLower >>> 8) ^ ByteModTable_7Lower[(bytes[i] ^ (int) IrredPolyLower) & 0xFF];
			IrredPolyHigher = (IrredPolyHigher >>> 8) ^ ByteModTable_7Higher[(bytes[i] ^ (int) IrredPolyHigher) & 0xFF];
		}
		
		return this;
	}

	/* (non-Javadoc)
	 * @see tlc2.util.Fingerprint#extend(char)
	 */
	public Fingerprint extend(char c) {
			
		// lower 64 bit
		IrredPolyLower = ((IrredPolyLower >>> 8) ^ (ByteModTable_7Lower[(((int) c) ^ ((int) IrredPolyLower)) & 0xFF]));
		// higher 64 bit
		IrredPolyHigher = ((IrredPolyHigher >>> 8) ^ (ByteModTable_7Higher[(((int) c) ^ ((int) IrredPolyHigher)) & 0xFF]));
	
		return this;
	}

	/* (non-Javadoc)
	 * @see tlc2.util.Fingerprint#extend(byte)
	 */
	public Fingerprint extend(byte b) {
			
		// lower 64 bit
		IrredPolyLower = ((IrredPolyLower >>> 8) ^ (ByteModTable_7Lower[(b ^ ((int) IrredPolyLower)) & 0xFF]));
		// higher 64 bit
		IrredPolyHigher = ((IrredPolyHigher >>> 8) ^ (ByteModTable_7Higher[(b ^ ((int) IrredPolyHigher)) & 0xFF]));
		
		return this;
	}

	/*
	 * Extend the fingerprint <code>fp</code> by an integer <code>x</code>.
	 */
	/* (non-Javadoc)
	 * @see tlc2.util.Fingerprint#extend(int)
	 */
	public FP128 extend(int x) {
			
		for (int i = 0; i < 4; i++) {
			byte b = (byte) (x & 0xFF);
			IrredPolyLower = ((IrredPolyLower >>> 8) ^ (ByteModTable_7Lower[(b ^ ((int) IrredPolyLower)) & 0xFF]));
			IrredPolyHigher = ((IrredPolyHigher >>> 8) ^ (ByteModTable_7Higher[(b ^ ((int) IrredPolyHigher)) & 0xFF]));
			x = x >>> 8;
		}

		return this;
	}
	/**
	 * 16 Bytes are what is needed to store a 128bit fingerprint (impressive, huh?) 
	 */
	public static final int BYTES = (2 * Long.SIZE) / Byte.SIZE;
	
	/*
	 * This is the table used for computing fingerprints. The ByteModTable could
	 * be hardwired. Note that since we just extend a byte at a time, we need
	 * just "ByteModeTable[7]".
	 */
	private static long[] ByteModTable_7Lower;
	private static long[] ByteModTable_7Higher;

	private static int indexLower;
	private static int indexHigher;
	
	// Initialization code
	private static void Init(int n) {
		indexLower = n;
		indexHigher = indexLower + 1 % numPolys;
		
		ByteModTable_7Lower = getByteModTable(Polys[indexLower]);
		ByteModTable_7Higher = getByteModTable(Polys[indexHigher]);
	}
	
	/* These are the irreducible polynomials used as seeds => 128bit */
	protected long IrredPolyLower;
	protected long IrredPolyHigher;
	
	protected FP128() {
		IrredPolyLower = Polys[indexLower];
		IrredPolyHigher = Polys[indexHigher];
	}
	
	private FP128(long lower, long higher) {
		IrredPolyLower = lower;
		IrredPolyHigher = higher;
	}

	public long[] getIrredPoly() {
		return new long[] {IrredPolyLower, IrredPolyHigher};
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "FP128 " + (isOnDisk() == true ? "(disk) " : "") + "[IrredPolyLower=" + IrredPolyLower + " (" +Long.toBinaryString(IrredPolyLower) + "), IrredPolyHigher="
				+ IrredPolyHigher + " (" + Long.toBinaryString(IrredPolyHigher) + ")]";
	}

	/* (non-Javadoc)
	 * @see tlc2.util.Fingerprint#getIndex(long)
	 */
	public int getIndex(final long mask) {
		return (int) ((IrredPolyLower ^ IrredPolyHigher) & mask);
	}

	private Fingerprint read(final java.io.RandomAccessFile raf) throws IOException {
		IrredPolyLower = raf.readLong();
		IrredPolyHigher = raf.readLong();
		return this;
	}

	/* (non-Javadoc)
	 * @see tlc2.util.Fingerprint#write(tlc2.util.BufferedRandomAccessFile)
	 */
	public void write(final java.io.RandomAccessFile raf) throws IOException {
		raf.writeLong(IrredPolyLower);
		raf.writeLong(IrredPolyHigher);
	}

	public void write(Unsafe u, long posLower, long posHigher) {
		u.putAddress(posLower, IrredPolyLower);
		u.putAddress(posHigher, IrredPolyHigher);
	}
	
	/* (non-Javadoc)
	 * @see tlc2.util.Fingerprint#longValue()
	 */
	public long longValue() {
		throw new UnsupportedOperationException("Not applicable for 128bit fingerprints");
	}

	public boolean isOnDisk()  {
		if ((IrredPolyHigher & 0x8000000000000000L) < 0) {
			return true;
		}
		return false;
	}

	public void setIsOnDisk() {
		// set msb to 1 to indicate fp is on disk
		this.IrredPolyHigher = IrredPolyHigher | 0x8000000000000000L;
	}

	public FP128 zeroMSB() {
		IrredPolyHigher = IrredPolyHigher & 0x7FFFFFFFFFFFFFFFL;
		return this;
	}
	
	public long getLower() {
		return IrredPolyLower;
	}

	public long getHigher() {
		return IrredPolyHigher & 0x7FFFFFFFFFFFFFFFL;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (IrredPolyHigher ^ (IrredPolyHigher >>> 32));
		result = prime * result + (int) (IrredPolyLower ^ (IrredPolyLower >>> 32));
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof FP128))
			return false;
		FP128 other = (FP128) obj;
		if (IrredPolyHigher != other.IrredPolyHigher)
			return false;
		if (IrredPolyLower != other.IrredPolyLower)
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(final Fingerprint other) {
		if (other instanceof FP128) {
			FP128 fp = (FP128) other;
			// zero msb of higher part which is 1 or 0 depending on disk state
			int compareTo = /* Long. */compare(
					IrredPolyHigher & 0x7FFFFFFFFFFFFFFFL,
					fp.IrredPolyHigher & 0x7FFFFFFFFFFFFFFFL);
			if (compareTo != 0) {
				return compareTo;
			} else {
				return /* Long. */compare(IrredPolyLower, fp.IrredPolyLower);
			}
		}
		throw new IllegalArgumentException();
	}
	
	/**
	 * @see Long#compare(long, long) in Java 1.7
	 */
	private int compare(long x, long y) {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
	}

	/**
	 * @param a
	 * @param b
	 * @return The maximum of both fingerprints.
	 */
	public static FP128 max(final FP128 a, final FP128 b) {
		if (a.compareTo(b) < 0) {
			return b;
		} else {
			return a;
		}
	}
}
