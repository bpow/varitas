package org.drpowell.vcffilters;

import org.drpowell.vcf.VCFVariant;

public class DefaultVCFFilter extends VCFFilteringIterator {
	
	public DefaultVCFFilter(VCFFilteringIterator variants) {
		super(variants);
	}

	public static final VCFVariant filterImpact(VCFVariant v) {
		String effect = v.getInfoValue("IMPACT");
		if (effect == null) return null;
		return ("HIGH".equals(effect) || "MODERATE".equals(effect)) ? v : null;
	}
	
	public static final VCFVariant filterLessThan(VCFVariant v, String key, double cutoff) {
		String afString = v.getInfoValue(key);
		if (afString == null || afString.isEmpty() || ".".equals(afString)) return v;
		try {
			double d = Double.valueOf(afString);
			return (d <= cutoff) ? v : null;
		} catch (NumberFormatException nfe) {
			// nothing to do
		}
		return v;
	}
	
	public static VCFVariant filterCombo(VCFVariant v) {
		String vFilter = v.getFilter();
		double cutoff = 0.01;
		return (("PASS".equals(vFilter) || ".".equals(vFilter)) &&
				filterImpact(v) != null &&
				filterLessThan(v, "NIEHSAF", cutoff) != null &&
				filterLessThan(v, "NIEHSIAF", cutoff) != null &&
				filterLessThan(v, "TGAF", cutoff) != null
				) ? v : null;
	}
	
	@Override
	public VCFVariant filter(VCFVariant v) {
		return filterCombo(v);
	}

}
