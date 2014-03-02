package com.chitek.ignition.drivers.generictcp.redundancy;

import java.io.Serializable;

public abstract class StateUpdate  implements Serializable {
	private static final long serialVersionUID = 1L;
	private final int id;
	
	public StateUpdate(int id) {
		this.id = id;
	}
	
	public int getId() {
		return id;
	}
	
}
