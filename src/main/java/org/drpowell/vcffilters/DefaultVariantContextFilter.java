package org.drpowell.vcffilters;


import htsjdk.variant.variantcontext.VariantContext;

import java.util.Set;

public class DefaultVariantContextFilter extends VariantContextFilteringIterator {
	
	public DefaultVariantContextFilter(VariantContextFilteringIterator variants) {
		super(variants);
	}

	public static final VariantContext filterImpact(VariantContext v) {
		String effect = v.getAttributeAsString("IMPACT", null);
		if (effect == null) return null;
		return ("HIGH".equals(effect) || "MODERATE".equals(effect)) ? v : null;
	}
	
	public static final VariantContext filterLessThan(VariantContext v, String key, double cutoff) {
		Double af = v.getAttributeAsDouble(key, 0);
		return (af <= cutoff) ? v : null;
	}
	
	public static VariantContext filterCombo(VariantContext v) {
		Set<String> vFilter = v.getFilters();
		double cutoff = 0.01;
		return (vFilter.isEmpty() &&
				filterImpact(v) != null &&
				filterLessThan(v, "NIEHSAF", cutoff) != null &&
				filterLessThan(v, "NIEHSIAF", cutoff) != null &&
				filterLessThan(v, "TGAF", cutoff) != null
				) ? v : null;
	}
	
	@Override
	public VariantContext filter(VariantContext v) {
		return filterCombo(v);
	}

}
