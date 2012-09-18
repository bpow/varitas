/* The MIT License

   Copyright (c) 2010 Broad Institute.
   Portions Copyright (c) 2012 Baylor College of Medicine.

   Permission is hereby granted, free of charge, to any person obtaining
   a copy of this software and associated documentation files (the
   "Software"), to deal in the Software without restriction, including
   without limitation the rights to use, copy, modify, merge, publish,
   distribute, sublicense, and/or sell copies of the Software, and to
   permit persons to whom the Software is furnished to do so, subject to
   the following conditions:

   The above copyright notice and this permission notice shall be
   included in all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
   SOFTWARE.
*/

package org.drpowell.tabix;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.sf.samtools.util.BinaryCodec;
import net.sf.samtools.util.BlockCompressedInputStream;

import org.drpowell.tabix.TabixIndex.Chunk;
import org.drpowell.tabix.TabixIndex.TabixConfig;

public class TabixReader
{
	public final String filename;
	BlockCompressedInputStream mFp;
	// private static Logger logger = Logger.getLogger(TabixReader.class.getCanonicalName());

	public final TabixIndex tabix;
	public final TabixConfig conf;
	private ArrayList<String> headers;

	static boolean less64(final long u, final long v) { // unsigned 64-bit comparison
		return (u < v) ^ (u < 0) ^ (v < 0);
	}
	
	private TabixIndex readHeader(BlockCompressedInputStream bcis) throws IOException {
		byte[] buf = new byte[4];

		bcis.read(buf, 0, 4); // read "TBI\1"
		BinaryCodec codec = new BinaryCodec(bcis);
		String [] mSeq = new String[codec.readInt()]; // # sequences
		int mPreset = codec.readInt();
		int sequenceColumn = codec.readInt();
		int beginColumn = codec.readInt();
		int endColumn = codec.readInt();
		int metaCharacter = codec.readInt();
		int linesToSkip = codec.readInt();
		
		TabixIndex t = new TabixIndex(mPreset, sequenceColumn, beginColumn, endColumn, (char) metaCharacter, linesToSkip);

		// read sequence dictionary
		int i, j, k, l = codec.readInt();
		buf = new byte[l];
		codec.readBytes(buf);
		for (i = j = k = 0; i < buf.length; ++i) {
			if (buf[i] == 0) {
				byte[] b = new byte[i - j];
				System.arraycopy(buf, j, b, 0, b.length);
				String s = new String(b);
				t.mChr2tid.put(s, k);
				mSeq[k++] = s;
				j = i + 1;
			}
		}

		// read the index
		for (i = 0; i < mSeq.length; ++i) {
			// the binning index
			int n_bin = codec.readInt();
			BinIndex binMap = new BinIndex(1 + n_bin * 4 / 3, 0.75f);
			t.binningIndex.add(binMap);
			for (j = 0; j < n_bin; ++j) {
				int bin = codec.readInt();
				int n_chunks = codec.readInt();
				ArrayList<TabixIndex.Chunk> chunks = new ArrayList<TabixIndex.Chunk>(n_chunks);
				for (k = 0; k < n_chunks; ++k) {
					long u = codec.readLong();
					long v = codec.readLong();
					chunks.add(new TabixIndex.Chunk(u, v)); // in C, this is inefficient
				}
				binMap.put(bin, chunks);
			}
			// the linear index
			int n_linear = codec.readInt();
			LinearIndex linear = new LinearIndex(n_linear);
			for (k = 0; k < n_linear; ++k)
				linear.add(codec.readLong());
			t.linearIndex.add(linear);
		}
		
		return t;
	}
	
	/**
	 * The constructor which will use the default index (basename + ".tbi")
	 *
	 * @param filename File name of the data file
	 */
	public TabixReader(final String filename) throws IOException {
		this(filename, filename+".tbi");
	}
	
	/**
	 * The constructor where a separate index file is specified.
	 * 
	 * @param filename
	 * @param indexFileName
	 * @throws IOException
	 */
	public TabixReader(final String filename, final String indexFileName) throws IOException {
		this.filename = filename;
		mFp = new BlockCompressedInputStream(new File(filename));
		BlockCompressedInputStream is = new BlockCompressedInputStream(new File(filename + ".tbi"));
		tabix = readHeader(is);
		conf = tabix.config;
		is.close();
	}

	public List<String> readHeaders() throws IOException {
		if (headers == null) {
			ArrayList<String> tmpHeaders = new ArrayList<String>(conf.linesToSkip);
			int skiplinesRemaining = conf.linesToSkip;
			mFp.seek(0);
			String line;
			while ((line = mFp.readLine()) != null) {
				skiplinesRemaining--;
				if (skiplinesRemaining >= 0 || line.charAt(0) == conf.commentChar) {
					tmpHeaders.add(line);
				} else {
					break;
				}
			}
			headers = tmpHeaders;
		}
		return Collections.unmodifiableList(headers);
	}
	
	public Iterator<String []> query(final int tid, final int beg, final int end) {
		return new TabixIterator(tabix, new GenomicInterval(beg, end, tid));
	}
	
	public Iterator<String []> query(final String reg) {
		return new TabixIterator(tabix, reg);
	}
	
	public static String join(String [] strings, String delimiter) {
		// the most-rewritten function in the java language
		if (strings.length == 0) return "";
		StringBuilder sb = new StringBuilder(strings[0]);
		for (int i = 1; i < strings.length; i++) {
			sb.append(delimiter).append(strings[i]);
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage: java -cp .:sam.jar TabixReader <in.gz> [region]");
			System.exit(1);
		}
		try {
			TabixReader tr = new TabixReader(args[0]);
//			String s;
			if (args.length != 1) { 
				// no region is specified; print the whole file
//				while ((s = tr.readLine()) != null)
//					System.out.println(s);
//			} else { // a region is specified; random access
				String [] row;
				Iterator<String []> iter = tr.query(args[1]); // get the iterator
				while (iter != null && (row = iter.next()) != null)
					System.out.println(join(row, "\t"));
			}
		} catch (IOException e) {
		}
	}

	public Integer getIdForChromosome(String chromosome) {
		return tabix.getIdForChromosome(chromosome);
	}
}
