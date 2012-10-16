package org.drpowell.vcf;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;

public class VCFMemoryCollection extends AbstractCollection<VCFVariant> {
	private final ArrayList<VCFVariant> variants = new ArrayList<VCFVariant>();
	private VCFHeaders headers;
	
	public VCFMemoryCollection(VCFParser parser) {
		for (VCFVariant v: parser) {
			variants.add(v);
		}
		headers = parser.getHeaders();
	}
	
	public VCFHeaders getHeaders() {
		return headers;
	}

	@Override
	public Iterator<VCFVariant> iterator() {
		return variants.iterator();
	}

	@Override
	public int size() {
		return variants.size();
	}

}
