/**
 * 
 */
package org.geneontology.minerva.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.geneontology.minerva.CoreMolecularModelManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.search.EntitySearcher;

import com.google.common.collect.Multimap;

/**
 * @author benjamingood
 *
 */
public class ActivityUnit extends GoCamOccurent{
	Set<BiologicalProcessUnit> containing_processes;
	Set<PhysicalEntity> enablers;

	private static Logger LOG = Logger.getLogger(ActivityUnit.class);

	public ActivityUnit(OWLNamedIndividual ind, OWLOntology ont, GoCamModel model) {
		super(ind, ont, model);
		enablers = new HashSet<PhysicalEntity>();
		containing_processes = new HashSet<BiologicalProcessUnit>();
		causal_out = new HashMap<OWLObjectProperty, Set<GoCamOccurent>>();
		causal_in = new HashMap<OWLObjectProperty, Set<GoCamOccurent>>();
		inputs = new HashSet<PhysicalEntity>();
		outputs = new HashSet<PhysicalEntity>();
		regulating_entities = new HashSet<PhysicalEntity>();
		locations = new HashSet<AnatomicalEntity>();
		transport_locations = new HashSet<AnatomicalEntity>();
		//FYI this doesn't work unless the gocam ontology either imports the declarations e.g. for all the object properties and classes
		//or includes the declarations.  
		Collection<OWLAxiom> ref_axioms = EntitySearcher.getReferencingAxioms(ind, ont);
		for(OWLAxiom axiom : ref_axioms) {
			if(axiom.getAxiomType().equals(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
				OWLObjectPropertyAssertionAxiom a = (OWLObjectPropertyAssertionAxiom) axiom;
				OWLObjectProperty prop = (OWLObjectProperty) a.getProperty();
				if(a.getSubject().equals(ind)) {
					OWLNamedIndividual object = a.getObject().asOWLNamedIndividual();
					if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002333")) {
						enablers.add(new PhysicalEntity(object, ont, model));
					}
					else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/BFO_0000050")) {
						containing_processes.add(new BiologicalProcessUnit(object, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002233")) {
						inputs.add(new PhysicalEntity(object, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002234")) {
						outputs.add(new PhysicalEntity(object, ont, model));
					}
					else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/BFO_0000066")) {
						locations.add(new AnatomicalEntity(object, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002339")) {
						transport_locations.add(new AnatomicalEntity(object, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002338")) {
						transport_locations.add(new AnatomicalEntity(object, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002313")) {
						transport_locations.add(new AnatomicalEntity(object, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002429")) {
						regulating_entities.add(new PhysicalEntity(object, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002430")) {
						regulating_entities.add(new PhysicalEntity(object, ont, model));
					}
					//all other properties now assumed to be causal relations 
					else {	
						GoCamOccurent object_event = getOccurent(object, ont, model);
						if(object_event!=null) {
							Set<GoCamOccurent> objects = causal_out.get(prop);
							if(objects==null) {
								objects = new HashSet<GoCamOccurent>();
							}
							objects.add(object_event);
							causal_out.put(prop, objects);
						}else {
							LOG.error("Linked prop "+prop+" Object Not an occurent "+object+ " in "+model.getIri()+" "+model.getTitle());
						}
					}
					//above we have contextual information for this activity
					//here we check for causal relations linked to this activity from another one.  
				}else if(a.getObject().equals(ind)) {
					//the source 
					OWLNamedIndividual source = a.getSubject().asOWLNamedIndividual();
					if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002429")) {
						regulating_entities.add(new PhysicalEntity(source, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002430")) {
						regulating_entities.add(new PhysicalEntity(source, ont, model));
					}else {
						GoCamEntity source_event = getOccurent(source, ont, model);
						if(source_event !=null) {
							Set<GoCamOccurent> sources = causal_in.get(prop);
							if(sources==null) {
								sources = new HashSet<GoCamOccurent>();
							}
							sources.add((GoCamOccurent)source_event);
							causal_in.put(prop, sources);
						}
					}
				}
			}
		}
	}	

	//causal_out = new HashMap<OWLObjectProperty, Set<GoCamOccurent>>();

	public Set<GoCamOccurent> getDownstream(GoCamOccurent activity, Set<GoCamOccurent> down){
		if(down==null) {
			down = new HashSet<GoCamOccurent>();
		}
		if(activity.causal_out!=null) {
			for(Set<GoCamOccurent> nextsteps : activity.causal_out.values()) {
				for(GoCamOccurent nextstep : nextsteps) {
					if(nextstep!=activity&&!down.contains(nextstep)) {
						down.add(nextstep);
						down = getDownstream(nextstep, down);
					}
				}
			}
		}
		return down;
	}


	private GoCamOccurent getOccurent(OWLNamedIndividual object, OWLOntology ont, GoCamModel model) {
		GoCamEntity e = model.ind_entity.get(object);
		if(e!=null) {
			if(e instanceof GoCamOccurent) {
				return (GoCamOccurent)e;
			}else {
				LOG.error("Tried to get physical entity as occurent "+object+ " in "+model.getIri()+" "+model.getTitle());
				return null;
			}
		}
		Set<String> types = model.ind_types.get(object);
		GoCamOccurent object_event = null;
		if(types==null) {
			LOG.error("No types found for "+object+ " in "+model.getIri()+" "+model.getTitle());
		}else {
			if(types.contains("http://purl.obolibrary.org/obo/GO_0008150")) {
				object_event = new BiologicalProcessUnit(object, ont, model);
			}else if(types.contains("http://purl.obolibrary.org/obo/GO_0003674")) {
				object_event = new ActivityUnit(object, ont, model);
			}else {
				LOG.error("Tried to get physical entity as occurent "+object+ " in "+model.getIri()+" "+model.getTitle());
				return null;
			}
		}
		if(object_event!=null) {
			model.ind_entity.put(object, object_event);
		}
		return object_event;
	}

	public String toString() {
		String g = "";
		if(label!=null) {
			g += "label:"+label;
		}
		g+="\nIRI:"+individual.toString()+"\ntypes: "+this.stringForClasses(this.direct_types);
		if(comments!=null) {
			g+="\ncomments: "+comments+"\n";
		}
		if(notes!=null) {
			g+="\nnotes:"+notes;
		}
		if(enablers!=null) {
			g+="\nenabled by "+enablers;
		}
		if(locations!=null) {
			g+="\noccurs in "+locations;
		}
		if(containing_processes!=null) {
			g+="\npart of "+containing_processes;
		}
		if(inputs!=null) {
			g+="\nhas inputs "+inputs;
		}
		if(outputs!=null) {
			g+="\nhas outputs "+outputs;
		}
		return g;
	}

	public Set<BiologicalProcessUnit> getContaining_processes() {
		return containing_processes;
	}

	public void setContaining_processes(Set<BiologicalProcessUnit> containing_processes) {
		this.containing_processes = containing_processes;
	}

	public Set<PhysicalEntity> getEnablers() {
		return enablers;
	}

	public void setEnablers(Set<PhysicalEntity> enablers) {
		this.enablers = enablers;
	}

	public String getURIsForConnectedBPs() {
		String bp_iris = "";
		Set<OWLClass> bps = new HashSet<OWLClass>();
		for(BiologicalProcessUnit bpu : getContaining_processes()) {
			bps.addAll(bpu.direct_types);
		}
		if(bps.size()>0) {
			bp_iris = this.stringForClasses(bps);
		}
		return bp_iris;
	}

	/**
	 * Definition of a 'complete' activity unit
	 * @return
	 */
	public boolean isComplete() {
		boolean complete = false;
		if(this.getEnablers().size()==1&&
				this.getLocations().size()==1&&
				this.getContaining_processes().size()==1&&
				this.getDirect_types().size()==1) {
			OWLClass type = this.getDirect_types().iterator().next();		
			if(!type.equals(this.in_model.mf)) {
				complete = true;
			}
		}
		return complete;
	}

}
