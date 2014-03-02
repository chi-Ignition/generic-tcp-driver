package com.chitek.ignition.drivers.generictcp.tests.folders;

import java.util.ArrayList;
import java.util.List;

import com.chitek.ignition.drivers.generictcp.folder.MessageFolder;
import com.inductiveautomation.opcua.types.DataValue;
import com.inductiveautomation.opcua.types.StatusCode;
import com.inductiveautomation.opcua.types.Variant;
import com.inductiveautomation.xopc.driver.api.items.ReadItem;
import com.inductiveautomation.xopc.driver.api.items.WriteItem;

public class FolderTestUtils {
	public static DataValue readValue(MessageFolder folder, String address) {
		MockReadItem readItem = new MockReadItem(address);
		List<ReadItem>items = new ArrayList<ReadItem>();
		items.add(readItem);
		folder.readItems(items);
		return readItem.getValue();
	}
	
	public static StatusCode writeValue(MessageFolder folder, String address, Variant value) {
		List<WriteItem> items = new ArrayList<WriteItem>();
		items.add(new MockWriteItem(address, value));
		folder.writeItems(items);
		return ((MockWriteItem) items.get(0)).getWriteStatus();
	}
}
