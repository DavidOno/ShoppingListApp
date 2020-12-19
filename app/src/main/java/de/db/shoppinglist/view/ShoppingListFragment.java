package de.db.shoppinglist.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import de.db.shoppinglist.R;
import de.db.shoppinglist.adapter.ShoppingListRecViewAdapter;
import de.db.shoppinglist.ifc.NewEntrySVM;
import de.db.shoppinglist.model.ShoppingElement;
import de.db.shoppinglist.model.ShoppingEntry;

public class ShoppingListFragment extends Fragment implements ShoppingListRecViewAdapter.OnEntryListener{

    private int positionToByModified;
    private RecyclerView entriesView;
    private FloatingActionButton newEntryButton;
    private ShoppingListRecViewAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shoppinglist, container, false);
        findViewsById(view);
        newEntryButton.setOnClickListener(v -> openNewEntryFragment());
        adapter = new ShoppingListRecViewAdapter(this);
        entriesView.setAdapter(adapter);
        entriesView.setLayoutManager(new LinearLayoutManager(getContext()));

        NewEntrySVM newEntrymodel = new ViewModelProvider(requireActivity()).get(NewEntrySVM.class);
        newEntrymodel.getProvided().observe(getViewLifecycleOwner(), item -> {
            String name = item.getName();
            float quantity = item.getQuantity();
            String unitOfQuantity = item.getUnitOfQuantity();
            String details = item.getDetails();
            adapter.addEntry(new ShoppingEntry(quantity, unitOfQuantity, new ShoppingElement(name, details)));
        });
        return view;
    }

    private void openNewEntryFragment() {
        NavController navController = NavHostFragment.findNavController(this);
        NavDirections newEntry = ShoppingListFragmentDirections.actionShoppingListFragmentToNewEntryFragment();
        navController.navigate(newEntry);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_shoppinglist, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void findViewsById(View view) {
        entriesView = view.findViewById(R.id.listOfEntries);
        newEntryButton = view.findViewById(R.id.AddNewEntryButton);
    }

    @Override
    public void onEntryClick(int position) {
        positionToByModified = position;
        ShoppingEntry entry = adapter.getEntryByPosition(position);
        NavController navController = NavHostFragment.findNavController(this);
        NavDirections openSelectedEntry = ShoppingListFragmentDirections.actionShoppingListFragmentToModifyEntryFragment(entry);
        navController.navigate(openSelectedEntry);
    }
}
