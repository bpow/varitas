TGwMAF: ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz.tbi
	date >> TGxMAF

ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz:
	curl -O ftp://ftp-trace.ncbi.nih.gov/1000genomes/ftp/release/20110521/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz

ncbiSNP135: ncbiSNP135.vcf.gz.tbi
	date >> ncbiSNP135

ncbiSNP135.vcf.gz:
	curl -o $@ ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/human_9606/VCF/v4.0/00-All.vcf.gz

CG54: Complete_Public_Genomes_54genomes_VQHIGH.vcf.gz.tbi
	date >> CG54

Complete_Public_Genomes_54genomes_VQHIGH.vcf.gz:
	curl ftp://ftp2.completegenomics.com/Public_Genome_Summary_Analysis/Complete_Public_Genomes_69genomes_VQHIGH_VCF.txt.bz2 | bzcat | bgzip -c > $@

%.vcf.gz.tbi: %.vcf.gz
	tabix -p vcf $<
