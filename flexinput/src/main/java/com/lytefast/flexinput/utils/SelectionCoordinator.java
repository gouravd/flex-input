package com.lytefast.flexinput.utils;

import android.support.v4.util.ArrayMap;
import android.support.v7.widget.RecyclerView;

import java.util.ArrayList;


/**
 * Manages selection logic for {@link android.support.v7.widget.RecyclerView.Adapter}s.
 *
 * @author Sam Shih
 */
public class SelectionCoordinator<T> {

  /**
   * Maintains a mapping from the selected item to the position in the adapter.
   */
  private final ArrayMap<T, Integer> selectedItemPositionMap;
  private RecyclerView.Adapter<?> adapter;
  private ItemSelectionListener itemSelectionListener;

  public SelectionCoordinator() {
    this.selectedItemPositionMap = new ArrayMap<>(4);
    this.itemSelectionListener = new ItemSelectionListener();
  }

  public SelectionCoordinator bind(RecyclerView.Adapter<?> adapter) {
    this.adapter = adapter;
    return this;
  }

  public void setItemSelectionListener(ItemSelectionListener listener) {
    this.itemSelectionListener = listener;
  }

  /**
   * @return List of positions which were previously selected. This should be used to update the
   * UI via {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemChanged(int)}
   */
  public ArrayList<Integer> clearSelectedItems() {
    ArrayList<Integer> oldSelection = new ArrayList<>(selectedItemPositionMap.values());
    selectedItemPositionMap.clear();
    for (int position: oldSelection) {
      adapter.notifyItemChanged(position);
    }
    return oldSelection;
  }

  public boolean isSelected(T item) {
    return selectedItemPositionMap.containsKey(item);
  }

  /**
   * Toggle the selection state for the item.
   *
   * @return True if the item was added. False otherwise.
   */
  public boolean toggleItem(T item, int position) {
    if (selectedItemPositionMap.remove(item) == null) {
      selectedItemPositionMap.put(item, position);
      itemSelectionListener.onItemSelected(item);
      return true;
    }
    itemSelectionListener.onItemUnselected(item);
    return false;
  }

  public static class ItemSelectionListener<T> {
    public void onItemSelected(T item) {}
    public void onItemUnselected(T item) {}
  }
}
