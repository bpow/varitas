package org.drpowell.xlifyvcf;

import java.util.Iterator;

import org.drpowell.util.FilteringIterator;
import org.drpowell.varitas.VCFVariant;

public class DefaultVCFFilter extends FilteringIterator<VCFVariant> {
	
	public DefaultVCFFilter(Iterator<VCFVariant> variants) {
		super(variants);
	}

	public static final boolean filterImpact(VCFVariant v) {
		String effect = v.getInfoField("IMPACT");
		if (effect == null) return false;
		return "HIGH".equals(effect) || "MODERATE".equals(effect);
	}
	
	public static final boolean filterAF(VCFVariant v, String key, double cutoff) {
		String afString = v.getInfoField(key);
		if (afString == null || afString.isEmpty() || ".".equals(afString)) return true;
		try {
			double d = Double.valueOf(afString);
			return d <= cutoff;
		} catch (NumberFormatException nfe) {
			// nothing to do
		}
		return true;
	}
	
	public static boolean filterCombo(VCFVariant v) {
		String vFilter = v.getFilter();
		double cutoff = 0.01;
		return (("PASS".equals(vFilter) || ".".equals(vFilter)) &&
				filterImpact(v) &&
				filterAF(v, "NIEHSAF", cutoff) &&
				filterAF(v, "NIEHSIAF", cutoff) &&
				filterAF(v, "TGAF", cutoff)
				); 
	}
	
	@Override
	public boolean filter(VCFVariant v) {
		return filterCombo(v);
	}

}
