package de.db.shoppinglist.database;

import android.util.Log;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.concurrent.atomic.AtomicBoolean;

import de.db.shoppinglist.model.ShoppingEntry;
import de.db.shoppinglist.model.ShoppingList;

public class FirebaseSource implements Source {

    private static FirebaseSource instance;
    private final String listsRootKey = "Lists";
    private final String entriesKey = "Entries";
    private final String firebaseTag = "FIREBASE";



    @Override
    public boolean addEntry(String listUid, ShoppingEntry newEntry) {
        AtomicBoolean wasSuccess = new AtomicBoolean(false);
        DocumentReference newEntryRef = FirebaseFirestore.getInstance()
                .collection(listsRootKey).document(listUid).collection(entriesKey).document(newEntry.getUid());
        newEntryRef.set(newEntry).addOnSuccessListener(aVoid -> {
            Log.d("FIREBASE", "Success: Added Entry");
            wasSuccess.set(true);
        })
                .addOnFailureListener(e -> {
                    Log.d("FIREBASE", e.getMessage());
                });;
        return wasSuccess.get();
    }

    @Override
    public boolean deleteEntry(String listUid, String documentUid) {
        AtomicBoolean wasSuccess = new AtomicBoolean(false);
        DocumentReference entryRef = FirebaseFirestore.getInstance().collection(listsRootKey).document(listUid).collection("Entries").document(documentUid);
        entryRef.delete().addOnSuccessListener(aVoid -> {
            Log.d("FIREBASE", "Success: Deleted Entry");
            wasSuccess.set(true);
        })
        .addOnFailureListener(e -> {
            Log.d("FIREBASE", e.getMessage());
        });
        return wasSuccess.get();
    }

    @Override
    public boolean addList(ShoppingList shoppingList) {
        AtomicBoolean wasSuccess = new AtomicBoolean(false);
        FirebaseFirestore.getInstance().collection(listsRootKey).document(shoppingList.getUid()).set(shoppingList);
        return wasSuccess.get();
    }

    @Override
    public boolean modifyList() {
        AtomicBoolean wasSuccess = new AtomicBoolean(false);
        return wasSuccess.get();
    }

    @Override
    public boolean deleteList(String listId) {
        boolean wasDeletingEntriesSuccess = deleteEntries(listId);
        boolean wasDeletingListSuccess = deleteListOnly(listId);
        return wasDeletingEntriesSuccess && wasDeletingListSuccess;
    }

    @Override
    public FirestoreRecyclerOptions<ShoppingEntry> getShoppingListRecyclerViewOptions(String listId) {
        Query query =  FirebaseFirestore.getInstance().collection(listsRootKey).document(listId).collection(entriesKey);
        return new FirestoreRecyclerOptions.Builder<ShoppingEntry>()
                .setQuery(query, ShoppingEntry.class)
                .build();
    }

    @Override
    public FirestoreRecyclerOptions<ShoppingList> getShoppingListsRecyclerViewOptions() {
        Query query = FirebaseFirestore.getInstance().collection(listsRootKey);
        return new FirestoreRecyclerOptions.Builder<ShoppingList>()
                .setQuery(query, ShoppingList.class)
                .build();
    }

    private boolean deleteEntries(String listId) {
        //From https://firebase.google.com/docs/firestore/manage-data/delete-data#collections
        // Deleting collections from an Android client is not recommended.
        return true;
    }

    private boolean deleteListOnly(String listId) {
        AtomicBoolean wasSuccess = new AtomicBoolean(true);
        DocumentReference entryRef = FirebaseFirestore.getInstance().collection(listsRootKey).document(listId);
        entryRef.delete().addOnSuccessListener(aVoid -> {
            Log.d("FIREBASE", "Success: Deleted List");

        }).addOnFailureListener(e -> {
            Log.d("FIREBASE", e.getMessage());
            wasSuccess.set(false);
        });
        return wasSuccess.get();
    }
}
