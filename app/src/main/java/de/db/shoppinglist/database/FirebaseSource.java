package de.db.shoppinglist.database;

import android.util.Log;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.db.shoppinglist.model.ShoppingEntry;
import de.db.shoppinglist.model.ShoppingList;

public class FirebaseSource implements Source {

    private final String listsRootKey = "Lists";
    private final String entriesKey = "Entries";
    private final String firebaseTag = "FIREBASE";
    private final CollectionReference rootCollectionRef = FirebaseFirestore.getInstance().collection(listsRootKey);


    @Override
    public boolean addEntry(String listUid, ShoppingEntry newEntry, boolean isPartOfModify) {
        AtomicBoolean wasSuccess = new AtomicBoolean(false);
        DocumentReference newEntryRef = rootCollectionRef.document(listUid).collection(entriesKey).document(newEntry.getUid());
        newEntryRef.set(newEntry).addOnSuccessListener(aVoid -> {
            if(!isPartOfModify)
                changeCounter(listUid, "total", 1);
            Log.d("FIREBASE", "Success: Added Entry");
            wasSuccess.set(true);
        })
                .addOnFailureListener(e -> {
                    Log.d("FIREBASE", e.getMessage());
                });;
        return wasSuccess.get();
    }

    @Override
    public boolean deleteEntry(String listUid, String documentUid, boolean isPartOfModify) {
        AtomicBoolean wasSuccess = new AtomicBoolean(false);
        DocumentReference entryRef = rootCollectionRef.document(listUid).collection("Entries").document(documentUid);
        entryRef.delete().addOnSuccessListener(aVoid -> {
            if(!isPartOfModify)
                changeCounter(listUid, "total", -1);
            Log.d("FIREBASE", "Success: Deleted Entry");
            wasSuccess.set(true);
        })
        .addOnFailureListener(e -> {
            Log.d("FIREBASE", e.getMessage());
        });
        return wasSuccess.get();
    }


    private  void changeCounter(String listUid, String counterName, int change) {
        final Task<DocumentSnapshot> documentSnapshotTask = rootCollectionRef.document(listUid).get();
        documentSnapshotTask.addOnSuccessListener(documentSnapshot -> {
            synchronized (this) {
                final AtomicReference<Long> counter = new AtomicReference<Long>(new Long(0));
                counter.set((Long) documentSnapshot.get(counterName));
                Map<String, Object> decrementTotal = new HashMap<>();
                int newCounter = (int) (counter.get() + change);
                decrementTotal.put(counterName, newCounter);
                Log.d("FIREBASE", "Counter_current:"+(int)counter.get().intValue() +" counter_later:"+newCounter);
                DocumentReference entryRef = rootCollectionRef.document(listUid);
                entryRef.update(decrementTotal)
                        .addOnSuccessListener(aVoid -> {
                            Log.d("FIREBASE", "Success: changed counter: " + change);
                        })
                        .addOnFailureListener(e -> {
                            Log.d("FIREBASE", e.getMessage());
                        });
            }
        });
    }

    @Override
    public boolean addList(ShoppingList shoppingList) {
        AtomicBoolean wasSuccess = new AtomicBoolean(false);
        rootCollectionRef.document(shoppingList.getUid()).set(shoppingList)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FIREBASE", "Success: Added List");
                    wasSuccess.set(true);
                })
                .addOnFailureListener(e -> {
                    Log.d("FIREBASE", e.getMessage());
                });;
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
        return wasDeletingEntriesSuccess;
    }

    @Override
    public FirestoreRecyclerOptions<ShoppingEntry> getShoppingListRecyclerViewOptions(String listId) {
        Query query = rootCollectionRef.document(listId).collection(entriesKey).orderBy("position");
        return new FirestoreRecyclerOptions.Builder<ShoppingEntry>()
                .setQuery(query, ShoppingEntry.class)
                .build();
    }

    @Override
    public FirestoreRecyclerOptions<ShoppingList> getShoppingListsRecyclerViewOptions() {
        Query lists = rootCollectionRef.orderBy("name");
        return new FirestoreRecyclerOptions.Builder<ShoppingList>()
                .setQuery(lists, ShoppingList.class)
                .build();
    }

    @Override
    public void updateEntryPosition(ShoppingList list, ShoppingEntry entry, int position) {
        Map<String, Object> updatePosition = new HashMap<>();
        updatePosition.put("position", position);
        rootCollectionRef.document(list.getUid()).collection(entriesKey).document(entry.getUid()).update(updatePosition);
    }

    @Override
    public void updateStatusDone(String listId, ShoppingEntry entry) {
        Map<String, Object> updateIsDone = new HashMap<>();
        updateIsDone.put("done", entry.isDone());
        rootCollectionRef.document(listId).collection(entriesKey).document(entry.getUid())
                .update(updateIsDone)
                .addOnSuccessListener(aVoid -> {
                    if(entry.isDone()){
                        changeCounter(listId, "done", 1);
                    }else{
                        changeCounter(listId, "done", -1);
                    }
                    Log.d("FIREBASE", "Success: Updated Status");
                })
                .addOnFailureListener(e -> {
                    Log.d("FIREBASE", e.getMessage());
                });
    }

    @Override
    public void updateListName(ShoppingList list) {
        Map<String, Object> updateName = new HashMap<>();
        updateName.put("name", list.getName());
        rootCollectionRef.document(list.getUid()).update(updateName)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FIREBASE", "Success: Updated Name");
                })
                .addOnFailureListener(e -> {
                    Log.d("FIREBASE", e.getMessage());
                });
    }

    @Override
    public void modifyWholeEntry(ShoppingList list, ShoppingEntry entry) {
        deleteEntry(list.getUid(), entry.getUid(), true);
        addEntry(list.getUid(), entry, true);
    }

    private boolean deleteEntries(String listId) {
        Task<QuerySnapshot> query = rootCollectionRef.document(listId).collection(entriesKey).get();
        query.addOnSuccessListener(aVoid -> {
            query.getResult().getDocuments().stream()
                    .map(doc -> buildPath(listId, doc))
                    .forEach(DocumentReference::delete);
            Log.d("FIREBASE", "Success: Deleted all entries");
            deleteListOnly(listId);
        });
        return true;
    }

    private DocumentReference buildPath(String listId, DocumentSnapshot doc) {
        return rootCollectionRef.document(listId).collection(entriesKey).document(doc.getId());
    }

    private boolean deleteListOnly(String listId) {
        AtomicBoolean wasSuccess = new AtomicBoolean(true);
        DocumentReference entryRef = rootCollectionRef.document(listId);
        entryRef.delete().addOnSuccessListener(aVoid -> {
            Log.d("FIREBASE", "Success: Deleted List");

        }).addOnFailureListener(e -> {
            Log.d("FIREBASE", e.getMessage());
            wasSuccess.set(false);
        });
        return wasSuccess.get();
    }
}
