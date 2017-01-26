package com.lytefast.flexinput.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.lytefast.flexinput.R;
import com.lytefast.flexinput.R2;
import com.lytefast.flexinput.adapters.AddContentPagerAdapter;
import com.lytefast.flexinput.adapters.AttachmentPreviewAdapter;
import com.lytefast.flexinput.model.Attachment;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;


/**
 * @author Sam Shih
 */
public class ViewPagerDialogFragment extends AppCompatDialogFragment {

  @BindView(R2.id.content_pager) ViewPager contentPager;
  @BindView(R2.id.content_tabs) TabLayout contentTabs;
  @BindView(R2.id.action_btn) FloatingActionButton actionButton;
  private Unbinder unbinder;

  @Override
  public Dialog onCreateDialog(final Bundle savedInstanceState) {
    AppCompatDialog dialog = new AppCompatDialog(getContext(), R.style.FlexInput_DialogWhenLarge);
    dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
    return dialog;
  }

  @Override
  public View onCreateView(final LayoutInflater inflater,
                           @Nullable final ViewGroup container,
                           @Nullable final Bundle savedInstanceState) {
    View root = inflater.inflate(R.layout.dialog_view_pager_with_fab, container, false);
    this.unbinder = ButterKnife.bind(this, root);

    FlexInputFragment parentFragment = (FlexInputFragment) getParentFragment();
    initContentPages(
        new AddContentPagerAdapter(getChildFragmentManager(), parentFragment.pageSuppliers));
    return root;
  }

  @Override
  public void onDestroyView() {
    unbinder.unbind();
    super.onDestroyView();
  }

  @OnClick(R2.id.content_root)
  void onContentRootClick() {
    final Dialog dialog = getDialog();
    if (isCancelable()) {  // TODO check setCanceledOnTouchOutside
      dialog.cancel();
    }
  }

  @OnClick(R2.id.action_btn)
  void onActionBtnClick() {
    dismiss();
  }

  protected ViewPagerDialogFragment initContentPages(@NonNull final AddContentPagerAdapter pagerAdapter) {
    pagerAdapter.initTabs(getContext(), contentTabs);
    contentPager.setAdapter(pagerAdapter);
    synchronizeTabAndPagerEvents();
    return this;
  }

  private void synchronizeTabAndPagerEvents() {
    contentTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
      /**
       * Special cases the first item (keyboard) by closing the pager and opening the keyboard on click.
       */
      @Override
      public void onTabSelected(final TabLayout.Tab tab) {
        int tabPosition = tab.getPosition();
        if (tabPosition == 0) {
          dismiss();
          return;
        }
        contentPager.setCurrentItem(tabPosition - 1);
      }

      @Override
      public void onTabUnselected(final TabLayout.Tab tab) { }

      @Override
      public void onTabReselected(final TabLayout.Tab tab) { }
    });

    contentPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) { }

      @Override
      public void onPageSelected(final int position) {
        contentTabs.getTabAt(position + 1).select();
      }

      @Override
      public void onPageScrollStateChanged(final int state) { }
    });
    // set the default to the first real tab
    contentTabs.getTabAt(1).select();
  }

}
