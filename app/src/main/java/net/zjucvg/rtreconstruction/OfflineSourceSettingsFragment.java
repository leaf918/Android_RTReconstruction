package net.zjucvg.rtreconstruction;

import android.os.Bundle;
import android.preference.PreferenceFragment;

public class OfflineSourceSettingsFragment extends PreferenceFragment {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.offline_source_preference);
  }

  public static OfflineSourceSettingsFragment newInstance() {
    return new OfflineSourceSettingsFragment();
  }
}
