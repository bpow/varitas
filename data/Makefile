TGwMAF: ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz.tbi
	date >> TGwMAF

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

# information at http://evs.gs.washington.edu
niehs95: niehs95.indels.vcf.gz.tbi niehs95.snps.vcf.gz.tbi
	date >> niehs95

niehs95.indels.vcf.gz: niehs.95samples.polymorphic.filtered.indels.vcf.gz
	gunzip -c < $< | bgzip -c > $@

niehs95.snps.vcf.gz: niehs.95samples.polymorphic.filtered.snps.vcf.gz
	gunzip -c < $< | bgzip -c > $@

niehs.95samples.polymorphic.filtered.indels.vcf.gz niehs.95samples.polymorphic.filtered.snps.vcf.gz:
	@echo --------------------------------------------------------------
	@echo This file must be downloaded manually using the Aspera client:
	@echo $@
	@echo
	@echo See http://evs.gs.washington.edu for more info
	@echo --------------------------------------------------------------
	@exit 1

ESP5400: ESP5400.vcf.gz.tbi
	date >> ESP5400

ESP5400.vcf.gz:
	curl -O http://evs.gs.washington.edu/esp5500_bulk_data/ESP5400.snps.vcf.tar.gz
	TMPDIR := $(shell mktemp -d)
	tar -C "$TMPDIR" xzf ESP5400.snps.vcf.tar.gz
	for chrom in `seq 1 22` X ; do \
		cat "$TMPDIR"/ESP5400.chr${chrom}.snps.vcf"; \
	done | bgzip -c > $@
	rm -rf "$TMPDIR"

dbNSFP2.0b1_variant.zip:
	curl -A "UA" -O http://dbnsfp.houstonbioinformatics.org/dbNSFPzip/dbNSFP2.0b1_variant.zip

dbNSFP2.0b1_variant.gz: dbNSFP2.0b1_variant.zip
	TMPDIR := $(shell mktemp -d)
	unzip -d "$TMPDIR" $<
	for chrom in `seq 1 22` X Y; do \
		cat "$TMPDIR"/*_variant.chr$i ; \
	done | bgzip -c > $@
	rm -rf "$TMPDIR"

dbNSFP2.0b1: dbNSFP2.0b1_variant.gz
	tabix -s1 -b2 -e2 $<
	date >>dbNSFP2.0b1

dbNSFP1.3.zip:
	curl -A "UA" -O http://dbnsfp.houstonbioinformatics.org/dbNSFPzip/dbNSFP1.3.zip

dbNSFP1.3.gz: dbNSFP1.3.zip
	TMPDIR := $(shell mktemp -d)
	echo $TMPDIR
	unzip -d "$TMPDIR" $<
	for chrom in `seq 1 22` X Y; do \
		cat "$TMPDIR"/dbNSFP1.3.chr$i ; \
	done | bgzip -c > $@
	rm -rf "$TMPDIR"

dbNSFP1.3: dbNSFP1.3.gz
	tabix -s1 -b2 -e2 $<
	date >>dbNSFP1.3

%.vcf.gz.tbi: %.vcf.gz
	tabix -p vcf $<
