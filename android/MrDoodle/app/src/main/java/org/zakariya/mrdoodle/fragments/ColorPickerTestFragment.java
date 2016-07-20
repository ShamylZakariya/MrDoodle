package org.zakariya.mrdoodle.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.view.ColorPickerView;
import org.zakariya.mrdoodle.view.ColorSwatchView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import icepick.Icepick;
import icepick.State;

/**
 * Created by shamyl on 11/7/15.
 */
public class ColorPickerTestFragment extends Fragment {

	@Bind(R.id.colorPicker)
	ColorPickerView colorPickerView;

	@Bind(R.id.inColorSwatch)
	ColorSwatchView inColorSwatch;

	@Bind(R.id.inTextInputLayout)
	TextInputLayout inTextInputLayout;

	@Bind(R.id.inEditText)
	EditText inEditText;

	@Bind(R.id.outColorSwatch)
	ColorSwatchView outColorSwatch;

	@Bind(R.id.outTextView)
	TextView outTextView;

	@State
	int inColor = 0xFF00FFFF; // defaults to Cyan

	@State
	int outColor = 0x0;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Icepick.restoreInstanceState(this, savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		Icepick.saveInstanceState(this, outState);
		super.onSaveInstanceState(outState);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_color_picker_test, container, false);
		ButterKnife.bind(this, v);

		inTextInputLayout.setErrorEnabled(true);
		inEditText.setText(hexString(inColor));
		inColorSwatch.setColor(inColor);

		colorPickerView.setInitialColor(inColor);

		if (outColor == 0x0) {
			outColor = colorPickerView.getCurrentColor();
		}

		outTextView.setText(hexString(outColor));
		outColorSwatch.setColor(outColor);

		inEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				//parseColorString(s.toString());
			}

			@Override
			public void afterTextChanged(Editable s) {
				String ss = s.toString();
				if (!ss.equals(hexString(inColor))) {
					parseColorString(ss);
				}
			}
		});

		colorPickerView.setOnColorChangeListener(new ColorPickerView.OnColorChangeListener() {
			@Override
			public void onColorChange(ColorPickerView view, int color) {
				outColor = color;
				outTextView.setText(hexString(outColor));
				outColorSwatch.setColor(outColor);
			}
		});

		return v;
	}

	@OnClick(R.id.inColorSwatch)
	void onInColorSwatchClick() {
		setInColor(inColorSwatch.getColor());
	}

	private void setInColor(int color) {
		inColor = Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
		inColorSwatch.setColor(inColor);
		colorPickerView.setInitialColor(inColor);
	}

	private String hexString(int color) {
		String hex = Integer.toHexString(color);
		return hex.substring(2); // chop off leading 'ff'
	}

	private void parseColorString(String colorString) {
		if (colorString.length() > 6) {
			inColorSwatch.setColor(0x0);
			inTextInputLayout.setError("Cannot have more than 6 chars");
			return;
		}

		inTextInputLayout.setError(null);

		try {
			int c = Integer.parseInt(colorString, 16);
			c = Color.rgb(Color.red(c), Color.green(c), Color.blue(c));
			setInColor(c);
		} catch (NumberFormatException nfe) {
			inColorSwatch.setColor(0x0);
			inTextInputLayout.setError("Unable to parse as hex color");
		}
	}
}
