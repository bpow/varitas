package org.drpowell.vcf;

import java.util.LinkedList;
import java.util.List;

public class VCFUtils {
	
	// this is the sort of thing that would be so much easier in Groovy/scala/ruby/python...
	public static final class FamilialTrio {
		public final String child, father, mother;
		public FamilialTrio(String child, String father, String mother) {
			this.child = child; this.father = father; this.mother = mother;
		}
		public int [] getIndices(List<String> samples) {
			int[] output = new int[3];
			output[0] = samples.indexOf(child);
			output[1] = samples.indexOf(father);
			output[2] = samples.indexOf(mother);
			return output;
		}
	}
	/**
	 * Parse the VCF headers looking for "PEDIGREE" lines where familial information can be encoded.
	 * At some point it would be nice to have full pedigree handling, but for now I want to be able
	 * to identify parent-child trios. This returns a list of such trios.
	 * @param headers
	 * @return
	 */
	public static List<int []> getTrioIndices(VCFHeaders headers) {
		List<int []> output = new LinkedList<int []>();
		List<String> samples = headers.getSamples();
		for (VCFMeta meta : headers) {
			if ("PEDIGREE".equals(meta.getMetaKey())) {
				FamilialTrio trio = new FamilialTrio(meta.getValue("Child"), meta.getValue("Father"), meta.getValue("Mother"));
				int [] indices = trio.getIndices(samples);
				for (int i = 0; i < indices.length; i++) {
					if (indices[i] < 0) continue;
				}
				output.add(indices);
			}
		}
		return output;
	}
}