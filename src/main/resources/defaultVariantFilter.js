// the passed "v" will be of type VCFVariant
// if the function "filter" returns "true", then the variant will be returned by the iterator
function filter(v) {
	var vFilter = v.getFilter()
	var cutoff = 0.01
	if (("PASS".equals(vFilter) || ".".equals(vFilter)) &&
			filterImpact(v) &&
			filterLessThan(v, "NIEHSAF", cutoff) &&
			filterLessThan(v, "NIEHSIAF", cutoff) &&
			filterLessThan(v, "TGAF", cutoff)
			) return v
	return null
}

importClass(java.lang.System)

function filterImpact(v) {
	var impact = v.getInfoValue("IMPACT");
	//System.err.println("impact: " + impact)
	if (impact == null) return false;
	return (impact.equals("HIGH") || impact.equals("MODERATE"))
}

function filterLessThan(v, key, cutoff) {
	var val = v.getInfoValue(key)
	//if (isNaN(val)) return v
	//return (parseFloat(val) <= cutoff) ? v : null
	// weird "not-greater-than" so NaNs will result in 'true'
	return (!(parseFloat(val) > cutoff))
}
