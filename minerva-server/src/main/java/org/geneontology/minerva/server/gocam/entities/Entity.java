package org.geneontology.minerva.server.gocam.entities;

public class Entity {
	private String uuid;
	private String id;
	private String label;
	private EntityType entityType;

	public Entity(String uuid, String id, String label, EntityType entityType) {
		this.uuid = uuid;
		this.id = id;
		this.label = label;
		this.entityType = entityType;
	}

	public String getUuid() {
		return uuid;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public EntityType getEntityType() {
		return entityType;
	}

	public void setEntityType(EntityType entityType) {
		this.entityType = entityType;
	}
}
