package org.drpowell.vcf;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;

public class VCFMemoryCollection extends AbstractList<VCFVariant> {
	private final ArrayList<VCFVariant> variants = new ArrayList<VCFVariant>();
	private VCFHeaders headers;
	
	public VCFMemoryCollection(VCFIterator input) {
		while (input.hasNext()) variants.add(input.next());
		headers = input.getHeaders();
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

	@Override
	public VCFVariant get(int index) {
		return variants.get(index);
	}

}
