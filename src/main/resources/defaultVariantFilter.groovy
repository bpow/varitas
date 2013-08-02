import org.drpowell.vcf.*

// the passed "v" will be of type VCFVariant
// if the function "filter" returns "true", then the variant will be returned by the iterator
def filter(VCFVariant v) {
	def cutoff = 0.01
	if (!filterImpact(v)) { v.addFilter("UNLIKELY_DELETERIOUS"); }
	if (!(v.getInfoValue("NIEHSAF")?.toFloat() <= cutoff &&
	    v.getInfoValue("NIEHSIAF")?.toFloat() <= cutoff &&
	    v.getInfoValue("TGAF")?.toFloat() <= cutoff)) {
		v.addFilter("COMMON")
	}
	return v
}

def numeric(x) {
	if (x == null) return 0;
	return x.toFloat()
}

def filterImpact(v) {
	def impact = v.getInfoValue("IMPACT");
	if (impact == null) { return false; }
	return (impact.equals("HIGH") || impact.equals("MODERATE"))
}

