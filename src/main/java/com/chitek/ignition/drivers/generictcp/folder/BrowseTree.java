package com.chitek.ignition.drivers.generictcp.folder;

import com.inductiveautomation.xopc.driver.util.TagTree;

public class BrowseTree extends TagTree<String> {

	/**
	 * Called by the message folders to add tags to the browse tree. Folder nodes are created when neccessary.
	 * The TagTree is a litte bit abused here. Normally addresses are seperated by '/' but i want arrays
	 * to appear as branches of the tree, so arrays are forced to be seperate by replacing the array indicator.
	 * The Tag element of the TreeNode contains the real, unmodified address of the node.
	 * 
	 * @param tag
	 */
	public TagTreeNode<String> addTag(String address) {
		String treeAddress = address.replaceAll("\\[(\\d+|(?:raw))\\]", "/[$1]");
		return super.addTag(treeAddress, address);
	}

	/**
	 * Returns the TreeNode with the given address. Array elements '[.]' are treated as subelements of the tree
	 */
	@Override
	public TagTreeNode<String> findTag(String address) {
		String treeAddress = address.replaceAll("\\[(\\d+|(?:raw))\\]", "/[$1]");
		return super.findTag(treeAddress);
	}
}
