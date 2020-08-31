package org.geneontology.minerva.model;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObjectProperty;

public class GoCamModelStats {
	int n_activity_units = 0;
	int n_complete_activity_units = 0;
	int n_connected_processes = 0;
	int n_causal_out_relation_assertions = 0;
	int n_unconnected = 0;
	int n_unconnected_out = 0;
	int n_unconnected_in = 0;
	int n_raw_mf = 0;
	int n_raw_bp = 0;
	int n_raw_cc = 0;
	int n_no_enabler = 0;
	int n_no_location = 0;
	int n_no_bp = 0;
	int max_connected_graph = 0;
	DescriptiveStatistics mf_depth = new DescriptiveStatistics();
	DescriptiveStatistics cc_depth = new DescriptiveStatistics();
	DescriptiveStatistics bp_depth = new DescriptiveStatistics();

	public GoCamModelStats(GoCamModel model) {
		if(model.activities==null) {
			return;
		}
		for(ActivityUnit a : model.activities) {
			n_activity_units++;
			Set<GoCamOccurent> downstream = a.getDownstream(a, null);
			for(OWLClass oc : a.direct_types) {
				try {
					int depth = -1;
					if(model.go_lego.class_depth!=null) {
						if(model.go_lego.class_depth.get(oc.getIRI().toString())!=null) {
							depth = model.go_lego.class_depth.get(oc.getIRI().toString());
						}else {
							//the class is probably deprecated
							Set<String> prob = new HashSet<String>(); prob.add(oc.getIRI().toString());
							Set<String> fixed = model.go_lego.replaceDeprecated(prob);
							if(fixed!=prob&&fixed.size()==1) {
								depth = model.go_lego.class_depth.get(fixed.iterator().next());
							}
						}
					}else {
						depth = model.go_lego.getClassDepth(oc.getIRI().toString(), "http://purl.obolibrary.org/obo/GO_0003674");
					}
					if(depth!=-1) {
						mf_depth.addValue(depth); 
					}
				} catch (IOException e) {
					//TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if(downstream.size()>max_connected_graph) {
				max_connected_graph = downstream.size();
			}
			if(a.direct_types.contains(model.mf)) {
				n_raw_mf++;
			}
			if(a.enablers.size()==0) {
				n_no_enabler++;
			}
			if(a.locations.size()==0) {
				n_no_location++;
			}			
			for(AnatomicalEntity ae : a.locations) {
				if(ae.direct_types.contains(model.cc)) {
					n_raw_cc++;
				}
				for(OWLClass oc : ae.direct_types) {
					if(oc==null||oc.isAnonymous()) {
						continue;
					}
					try {
						int depth = -1;
						if(model.go_lego.class_depth!=null) {
							Integer d = model.go_lego.class_depth.get(oc.getIRI().toString());
							if(d!=null) {
								depth = d;
							}
						}else {
							depth = model.go_lego.getClassDepth(oc.getIRI().toString(), "http://purl.obolibrary.org/obo/GO_0005575");
						}
						if(depth!=-1) {
							cc_depth.addValue(depth); 
						}
					} catch (IOException e) {
						//TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			if(a.containing_processes.size()==0) {
				n_no_bp++;
			}
			for(BiologicalProcessUnit bpu : a.containing_processes) {
				if(bpu.direct_types.contains(model.bp)) {
					n_raw_bp++;
				}
				for(OWLClass bp : bpu.direct_types) {
					try {
						int depth = -1;
						if(model.go_lego.class_depth!=null) {
							Integer d = model.go_lego.class_depth.get(bp.getIRI().toString());
							if(d!=null) {
								depth = d; 
							}else {
								System.out.println("missing "+bp.getIRI());
							}
						}else {
							depth = model.go_lego.getClassDepth(bp.getIRI().toString(), "http://purl.obolibrary.org/obo/GO_0008150");
						}
						if(depth!=-1) {
							bp_depth.addValue(depth); 
						}
					} catch (IOException e) {
						//TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			if(a.causal_out.size()==0) {
				n_unconnected_out++;
			}
			if(a.causal_in.size()==0) {
				n_unconnected_in++;
			}
			if(a.causal_in.size()==0&&a.causal_out.size()==0) {
				n_unconnected++;
			}
			if((a.containing_processes.size()==1)&&
					(a.enablers.size()==1)&&
					(a.locations.size()==1)&&
					(!a.direct_types.contains(model.mf))) {
				n_complete_activity_units++;
			}
			Set<String> p = new HashSet<String>();
			if(a.containing_processes!=null) {
				for(BiologicalProcessUnit bpu : a.containing_processes) {
					p.add(bpu.individual.toString());
				}
			}
			n_connected_processes = p.size();
			if(a.causal_out!=null) {
				for(OWLObjectProperty prop : a.causal_out.keySet()) {
					Set<GoCamOccurent> ocs = a.causal_out.get(prop);
					for(GoCamOccurent oc : ocs ) {
						n_causal_out_relation_assertions++;
					}
				}
			}
		}
	}

	public String stats2string(DescriptiveStatistics stats) {
		String g = "";
		g+="\t N:"+stats.getN()+"\n";
		g+="\t mean:"+stats.getMean()+"\n";
		g+="\t median:"+stats.getPercentile(50)+"\n";
		g+="\t max:"+stats.getMax()+"\n";
		g+="\t min:"+stats.getMin()+"\n";
		return g;
	}

	public String toString() {
		String g =" activity units "+n_activity_units+"\n";
		g+=" n complete activity units "+n_complete_activity_units+"\n";
		g+=" n root MF activity units "+n_raw_mf+"\n";
		g+=" n root BP process "+n_raw_bp+"\n";
		g+=" n root CC locations "+n_raw_cc+"\n";
		g+=" n unenabled activity units "+n_no_enabler+"\n";
		g+=" n unlocated activity units "+n_no_location+"\n";
		g+=" n activity units unconnected to a BP "+n_no_bp+"\n";
		g+=" n connected biological processes "+n_connected_processes+"\n";
		g+=" n causal relation assertions "+n_causal_out_relation_assertions+"\n";
		g+=" n unconnected activities "+n_unconnected+"\n";
		g+=" n activities with no outgoing connections "+n_unconnected_out+"\n";
		g+=" n activities with no incoming connections "+n_unconnected_in+"\n";
		g+=" max length of connected causal subgraph "+max_connected_graph+"\n";
		g+=" descriptive statistics for depth in ontology for MF terms defining activity units \n"+stats2string(mf_depth);
		g+=" descriptive statistics for depth in ontology for BP terms containing activity units \n"+stats2string(bp_depth);
		g+=" descriptive statistics for depth in ontology for CC terms used as locations for activity units \n"+stats2string(cc_depth);
		return g;
	}

	public String stats2cols() {
		String r = n_activity_units+"\t"+n_complete_activity_units+"\t"+n_raw_mf+"\t"+n_raw_bp+"\t"+n_raw_cc+"\t"+n_no_enabler+"\t"+n_no_location+"\t"+n_no_bp+
				"\t"+n_connected_processes+"\t"+n_causal_out_relation_assertions+"\t"+n_unconnected+"\t"+n_unconnected_out+"\t"+n_unconnected_in+"\t"+max_connected_graph+
				"\t"+mf_depth.getPercentile(50)+"\t"+bp_depth.getPercentile(50)+"\t"+cc_depth.getPercentile(50);
		return r;
	}
	public static String statsHeader() {
		String h = "activity units\tn complete activity units\tn root MF activity units\tn root BP process\tn root CC locations"
				+ "\tn unenabled activity units\tn unlocated activity units\tn activity units unconnected to a BP\tn connected biological processes"
				+ "\tn causal relation assertions\tn unconnected activities\tn activities with no outgoing connections\tn activities with no incoming connections"
				+ "\tmax length of connected causal subgraph\tmedian_depth_MF\tmedian_depth_BP\tmedian_depth_cc";
		return h;
	}
}
