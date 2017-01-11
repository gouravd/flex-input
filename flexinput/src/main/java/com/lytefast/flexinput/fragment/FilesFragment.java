package com.lytefast.flexinput.fragment;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.lytefast.flexinput.R;
import com.lytefast.flexinput.adapters.FileListAdapter;
import com.lytefast.flexinput.model.Attachment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;


/**
 * Fragment that displays the recent files for selection.
 *
 * @author Sam Shih
 */
public class FilesFragment extends Fragment implements AttachmentSelector<Attachment> {
  private RecyclerView recyclerView;

  /**
   * Mandatory empty constructor for the fragment manager to instantiate the
   * fragment (e.g. upon screen orientation changes).
   */
  public FilesFragment() {
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_recycler_view, container, false);

    if (view instanceof RecyclerView) {
      recyclerView = (RecyclerView) view;
      DividerItemDecoration bottomPadding =
          new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
      recyclerView.addItemDecoration(bottomPadding);
      recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }
    return view;
  }

  @Override
  public void onStart() {
    super.onStart();
    loadDownloadFolder();
  }

  private void loadDownloadFolder() {
    File downloadFolder =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    recyclerView.setAdapter(new FileListAdapter(getContext().getContentResolver(), downloadFolder));
  }


  @Override
  public Collection<Attachment> getSelectedAttachments() {
    Set<File> files = ((FileListAdapter) recyclerView.getAdapter()).getSelectedItems();

    ArrayList<Attachment> attachments = new ArrayList<>(files.size());
    for (File f : files) {
      attachments.add(new Attachment(f.hashCode(), Uri.fromFile(f), f.getName()));
    }
    return attachments;
  }

  @Override
  public void clearSelectedAttachments() {
    ((FileListAdapter) recyclerView.getAdapter()).clearSelectedItems();
  }
}