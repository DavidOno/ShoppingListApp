package de.db.shoppinglist.adapter.viewholder;

import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import de.db.shoppinglist.R;
import de.db.shoppinglist.adapter.FireShoppingListRecViewAdapter;
import de.db.shoppinglist.adapter.FireShoppingListRecViewAdapter.OnEntryListener;
import de.db.shoppinglist.model.ShoppingEntry;

public class DefaultViewHolder extends FireShoppingListRecViewAdapter.ViewHolder{
    public static final String NO_TEXT = "";
    public static final String MULTIPLIER = " x ";
    public static final double EPSILON = 0.0001;
    private TextView nameOfEntry;
    private TextView quantity;
    private TextView unitOfQuantity;
    private CheckBox isDone;
    private ImageButton dropDown;
    private TextView details;
    private OnEntryListener onEntryListener;
    private FireShoppingListRecViewAdapter adapter;

    public DefaultViewHolder(@NonNull View itemView, OnEntryListener onEntryListener, FireShoppingListRecViewAdapter adapter) {
        super(itemView);
        findViewsById(itemView);
        this.onEntryListener = onEntryListener;
        itemView.setOnClickListener(this);
        this.adapter = adapter;
    }

    private void findViewsById(@NonNull View itemView) {
        nameOfEntry = itemView.findViewById(R.id.entry_name_textview);
        quantity = itemView.findViewById(R.id.entry_quantity_textview);
        unitOfQuantity = itemView.findViewById(R.id.entry_unit_of_quantity_textview);
        isDone = itemView.findViewById(R.id.entry_isDoneCheckbox);
        dropDown = itemView.findViewById(R.id.entry_dropDownButton);
        details = itemView.findViewById(R.id.entry_details);
    }

    @Override
    public void onClick(View v) {
            onEntryListener.onEntryClick(getAdapterPosition());
        }

    @Override
    public void onBindViewHolder(FireShoppingListRecViewAdapter.ViewHolder holder, int position, ShoppingEntry shoppingEntry) {
        initHolderProperties(shoppingEntry);
        onCheckedChangeListenerForDone(shoppingEntry);
        strikeItemsThroughIfDone();
        final boolean isExpanded = setVisibilityOfDetails(position);
        textChangeListenerForDetails();
        manageDropDownIconVisibility();
        manageDropDownBehaviour(position, isExpanded);
    }

    private void initHolderProperties(ShoppingEntry shoppingEntry) {
        nameOfEntry.setText(shoppingEntry.getName());
        quantity.setText(getQuantityText(shoppingEntry.getQuantity()));
        unitOfQuantity.setText(shoppingEntry.getUnitOfQuantity());
        isDone.setChecked(shoppingEntry.isDone());
        details.setText(shoppingEntry.getDetails());
    }


    private void onCheckedChangeListenerForDone(ShoppingEntry shoppingEntry){
        isDone.setOnClickListener(view -> {
            adapter.setEntryContainingCheckedBox(shoppingEntry);
            adapter.setWasChecked(true);
        });
    }


    private boolean setVisibilityOfDetails(int position) {
        final boolean isExpanded = position == adapter.getExpandedPosition();
        details.setVisibility(isExpanded? View.VISIBLE:View.GONE);
        return isExpanded;
    }

    private void strikeItemsThroughIfDone() {
        if(isDone.isChecked()){
            strikeAllTextPropertiesThrough();
        }else{
            unstrikeAllTextProperties();
        }
    }

    private void unstrikeAllTextProperties() {
        nameOfEntry.setPaintFlags(nameOfEntry.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        quantity.setPaintFlags(quantity.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        unitOfQuantity.setPaintFlags(unitOfQuantity.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        details.setPaintFlags(details.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
    }

    private void strikeAllTextPropertiesThrough() {
        nameOfEntry.setPaintFlags(nameOfEntry.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        quantity.setPaintFlags(quantity.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        unitOfQuantity.setPaintFlags(unitOfQuantity.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        details.setPaintFlags(details.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
    }

    private void textChangeListenerForDetails() {
        details.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //not required
            }

            @Override
            public void onTextChanged(CharSequence sequence, int start, int before, int count) {
                manageDropDownIconVisibility();
            }

            @Override
            public void afterTextChanged(Editable s) {
                //not required
            }
        });
    }

    private void manageDropDownIconVisibility() {
        if (details.getText().toString().isEmpty()) {
            dropDown.setVisibility(View.INVISIBLE);
        } else {
            dropDown.setVisibility(View.VISIBLE);
        }
    }

    private void manageDropDownBehaviour(int position, boolean isExpanded) {
        dropDown.setActivated(isExpanded);
        if(isExpanded){
            adapter.setPreviousExpandedPosition(position);
        }
        dropDown.setOnClickListener(v -> {
            adapter.setExpandedPosition(isExpanded ? -1:position);
            adapter.notifyItemChanged(adapter.getPreviousExpandedPosition());
            adapter.notifyItemChanged(position);
        });
    }

    private String getQuantityText(float quantity) {
        if(isZero(quantity)){
            return NO_TEXT;
        }else{
            if(isInteger(quantity)){
                int quantityAsInt = (int) quantity;
                return quantityAsInt + MULTIPLIER;
            }
            return quantity + MULTIPLIER;
        }
    }

    private boolean isInteger(float quantity) {
        return (int)quantity == quantity;
    }

    private boolean isZero(float quantity) {
        return quantity - 0 < EPSILON;
    }

}
