package com.chitek.ignition.drivers.generictcp.tests.folders;

import java.util.ArrayList;
import java.util.List;

import com.chitek.ignition.drivers.generictcp.folder.MessageFolder;
import com.inductiveautomation.xopc.driver.api.items.ReadItem;
import com.inductiveautomation.xopc.driver.api.items.WriteItem;

public class FolderTestUtils {
	public static org.eclipse.milo.opcua.stack.core.types.builtin.DataValue readValue(MessageFolder folder, String address) {
		MockReadItem readItem = new MockReadItem(address);
		List<ReadItem>items = new ArrayList<ReadItem>();
		items.add(readItem);
		folder.readItems(items);
		return readItem.getValue();
	}
	
	public static org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode writeValue(MessageFolder folder, String address, org.eclipse.milo.opcua.stack.core.types.builtin.Variant value) {
		List<WriteItem> items = new ArrayList<WriteItem>();
		items.add(new MockWriteItem(address, value));
		folder.writeItems(items);
		return ((MockWriteItem) items.get(0)).getWriteStatus();
	}
}
