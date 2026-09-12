package com.termux.app.terminal;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import com.termux.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TermuxSessionsListRowTest {

    private View inflateRow() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight);
        return LayoutInflater.from(activity)
            .inflate(R.layout.item_terminal_sessions_list, null, false);
    }

    @Test
    public void sessionRowDoesNotBlockListViewItemClicks() {
        View row = inflateRow();
        assertThat(row.hasExplicitFocusable()).isFalse();
    }

    @Test
    public void closeButtonRemainsClickable() {
        ImageButton close = inflateRow().findViewById(R.id.session_close);
        assertThat(close.isClickable()).isTrue();
    }
}
