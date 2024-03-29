package de.db.shoppinglist.database;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import de.db.shoppinglist.model.EntryHistoryElement;
import de.db.shoppinglist.model.ShoppingEntry;
import de.db.shoppinglist.model.ShoppingList;
import de.db.shoppinglist.utility.ToastUtility;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * This class is responsible for providing basic CRUD functionalities
 * for shopping-lists and shopping-entries. All functions use firebase.
 * Every user has his own directory, where he can manage his shopping-lists.
 * All functions automatically write inside the user-specific directory.
 */
public class FirebaseSource implements Source {
    /** Firebase-Constant, representing the user-specific directory.*/
    public static final String USER_ROOT_KEY = "Users";
    /** Firebase-Constant, representing the list collection.*/
    public static final String LISTS_ROOT_KEY = "Lists";
    /**Firebase-Constant, representing the entries collection.*/
    public static final String ENTRIES_KEY = "Entries";
    /**Firebase-Constant, representing the history collection.*/
    public static final String HISTORY_KEY = "History";
    /**Firebase-Constant, representing the total-counter-property of a list.*/
    public static final String TOTAL_PROPERTY = "total";
    /**Firebase-Constant, representing the position-property of an entry.*/
    public static final String POSITION_PROPERTY = "position";
    /**Firebase-Constant, representing the name-property of an entry.*/
    public static final String NAME_PROPERTY = "name";
    /**Firebase-Constant, representing the done-property of an entry.*/
    public static final String DONE_PROPERTY = "done";
    /**Firebase-Constant, representing the details-property of an entry.*/
    public static final String DETAILS_PROPERTY = "details";
    /**Firebase-Constant, representing the quantity-property of an entry.*/
    public static final String QUANTITY_PROPERTY = "quantity";
    /**Firebase-Constant, representing the unit-of-quantiyt-property of an entry.*/
    public static final String UNIT_OF_QUANTITY_PROPERTY = "unitOfQuantity";
    /**Firebase-Constant, representing the next-free-position-property of a list.*/
    public static final String NEXT_FREE_POSITION_PROPERTY = "nextFreePosition";
    /**Firestore-Constant, representing the name of the folder, containing the images.*/
    public static final String IMAGE_STORAGE_KEY = "uploads";
    /**Firebase-Constant, representing image-uri-property of an entry.*/
    public static final String IMAGE_URI_PROPERTY = "imageURI";
    /**Firebase-Constant, representing the uid of an history-entry.*/
    public static final String HIST_UID_PROPERTY = "uid";
    /**Firebase-Constant, representing a collection, which stores all user metadata.*/
    public static final String USERS_KEY = "User";

    private final ToastUtility toastMaker = ToastUtility.getInstance();
    private static final String FIREBASE_TAG = "FIREBASE";

    private CollectionReference getListsRootCollectionRef() {
        String uid = getUserId();
        return FirebaseFirestore.getInstance().collection(USER_ROOT_KEY).document(uid).collection(LISTS_ROOT_KEY);
    }

    private String getUserId() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Log.d(FIREBASE_TAG, "FirebaseAuth returned null for userId");
        }
        return uid;
    }

    private CollectionReference getHistoryRootCollectionRef() {
        String uid = getUserId();
        return FirebaseFirestore.getInstance().collection(USER_ROOT_KEY).document(uid).collection(HISTORY_KEY);
    }

    /**
     * Adds an entry to a specific list.
     * During this process, also the counters (done & total entries) of the list will be updated and
     * this entry will be added to history.
     *
     * @param listId   The list-id, to which this entry should be added.
     * @param newEntry The new entry, which should be added.
     * @param context  The application context.
     */
    @Override
    public void addEntry(String listId, ShoppingEntry newEntry, Context context) {
        DocumentReference newEntryRef = getListsRootCollectionRef().document(listId).collection(ENTRIES_KEY).document(newEntry.getUid());
        newEntryRef.set(newEntry)
                .addOnSuccessListener(aVoid -> {
                    updateListStatusCounter(listId);
                    handleImageUpdate(listId, newEntry, context);
                    Log.d(FIREBASE_TAG, "Success: Added Entry");
                })
                .addOnFailureListener(e -> {
                            Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                            toastMaker.prepareToast("Fail: Add new Entry");
                        }
                );
        updateListInformation(listId, newEntry);
    }

    private void handleImageUpdate(String listId, ShoppingEntry newEntry, Context context) {
        if (isUploadUri(newEntry)) {
            uploadImage(listId, newEntry, context);
        } else {
            updateImage(listId, newEntry);
        }
    }

    private void updateListInformation(String listId, ShoppingEntry newEntry) {
        Map<String, Object> updateNextFreePosition = new HashMap<>();
        updateNextFreePosition.put(NEXT_FREE_POSITION_PROPERTY, newEntry.getPosition());
        getListsRootCollectionRef().document(listId).update(updateNextFreePosition)
                .addOnSuccessListener(aVoid -> {
                    updateListStatusCounter(listId);
                    Log.d(FIREBASE_TAG, "Success: Updated nextFreePosition");
                })
                .addOnFailureListener(e -> {
                            Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                            toastMaker.prepareToast("Fail: Updated \"next free position\"");
                        }
                );
    }

    private void addToHistory(ShoppingEntry newEntry) {
        getHistory().addOnSuccessListener(snapshots -> {
            Set<EntryHistoryElement> collectedHistory = collectHistoryAsSet(snapshots);
            boolean alreadyContained = collectedHistory.contains(newEntry.extractHistoryElement());
            if (!alreadyContained) {
                addNewElementToHistory(newEntry);
            }
        });
    }

    private void addNewElementToHistory(ShoppingEntry newEntry) {
        EntryHistoryElement historyElement = newEntry.extractHistoryElement();
        getHistoryRootCollectionRef().document(historyElement.getUid()).set(historyElement)
                .addOnSuccessListener(aVoid ->
                        Log.d(FIREBASE_TAG, "Success: Added to History")
                )
                .addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Add To History");
                });
    }

    private Set<EntryHistoryElement> collectHistoryAsSet(QuerySnapshot snapshots) {
        return snapshots.getDocuments()
                .stream()
                .map(this::makeHistoryElement)
                .collect(toSet());
    }

    /**
     * Deletes an entry.
     * During this process, also the counters (done & total entries) of the list will be updated.
     *
     * @param listId      Id of the list, containg this entry.
     * @param documentUid Id of the entry, which should be deleted.
     */
    @Override
    public void deleteEntry(String listId, String documentUid) {
        DocumentReference entryRef = getListsRootCollectionRef().document(listId).collection(ENTRIES_KEY).document(documentUid);
        entryRef.delete()
                .addOnSuccessListener(aVoid -> {
                    updateListStatusCounter(listId);
                    Log.d(FIREBASE_TAG, "Success: Deleted Entry");
                })
                .addOnFailureListener(e -> {
                            Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                            toastMaker.prepareToast("Fail: Delete Entry");
                        }
                );
    }

    private void updateListStatusCounter(String listId) {
        Task<QuerySnapshot> querySnapshotTask = getListsRootCollectionRef().document(listId).collection(ENTRIES_KEY).get();
        querySnapshotTask.addOnSuccessListener(queryDocumentSnapshots -> {
            long done = queryDocumentSnapshots.getDocuments().stream().filter(doc -> (Boolean) doc.get(FirebaseSource.DONE_PROPERTY)).count();
            long total = queryDocumentSnapshots.getDocuments().size();
            Map<String, Object> counterVars = buildMapForUpdate(done, total);
            getListsRootCollectionRef().document(listId).update(counterVars)
                    .addOnSuccessListener(aVoid ->
                            Log.d(FIREBASE_TAG, "Success: " + done + "/" + total)
                    )
                    .addOnFailureListener(e -> {
                                Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                                toastMaker.prepareToast("Fail: Update List Counter");
                            }
                    );
        });
    }

    private Map<String, Object> buildMapForUpdate(long done, long total) {
        Map<String, Object> counterVars = new HashMap<>();
        counterVars.put(DONE_PROPERTY, done);
        counterVars.put(TOTAL_PROPERTY, total);
        return counterVars;
    }

    /**
     * Adds a new shopping-list to firebase.
     *
     * @param shoppingList The new shopping-list.
     */
    @Override
    public void addList(ShoppingList shoppingList) {
        getListsRootCollectionRef().document(shoppingList.getUid()).set(shoppingList)
                .addOnSuccessListener(aVoid ->
                        Log.d(FIREBASE_TAG, "Success: Added List")
                )
                .addOnFailureListener(e -> {
                            Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                            toastMaker.prepareToast("Fail: Add List");
                        }
                );
    }

    /**
     * Deletes a list from firebase.
     *
     * @param listId Id of the list, which is supposed to be deleted.
     */
    @Override
    public void deleteList(String listId) {
        Task<QuerySnapshot> query = getListsRootCollectionRef().document(listId).collection(ENTRIES_KEY).get();
        query.addOnSuccessListener(aVoid -> {
            List<DocumentSnapshot> documents = Objects.requireNonNull(query.getResult()).getDocuments();
            AtomicInteger docsToDelete = new AtomicInteger(documents.size());
            documents.stream()
                    .map(doc -> buildPathForEntryDoc(listId, doc))
                    .forEach(doc -> deleteEntry(listId, docsToDelete, doc));
            deleteListIfAllDocWereDeleted(listId, docsToDelete);
            Log.d(FIREBASE_TAG, "Success: Deleted all entries");
        }).addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Delete List");
                }
        );
    }

    private void deleteListIfAllDocWereDeleted(String listId, AtomicInteger docsToDelete) {
        if (docsToDelete.get() == 0) {
            deleteListOnly(listId);
        }
    }

    private void deleteEntry(String listId, AtomicInteger docsToDelete, DocumentReference doc) {
        doc.delete().addOnSuccessListener(aVoid -> {
            docsToDelete.decrementAndGet();
            deleteListIfAllDocWereDeleted(listId, docsToDelete);
            Log.d(FIREBASE_TAG, "Deleted entry");
        }).addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Delete Entry");
                }
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FirestoreRecyclerOptions<ShoppingEntry> getShoppingListRecyclerViewOptions(String listId) {
        Query query = getListsRootCollectionRef().document(listId).collection(ENTRIES_KEY).orderBy(POSITION_PROPERTY);
        return new FirestoreRecyclerOptions.Builder<ShoppingEntry>()
                .setQuery(query, ShoppingEntry.class)
                .build();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public FirestoreRecyclerOptions<ShoppingList> getShoppingListsRecyclerViewOptions() {
        Query lists = getListsRootCollectionRef().orderBy(NAME_PROPERTY);
        return new FirestoreRecyclerOptions.Builder<ShoppingList>()
                .setQuery(lists, ShoppingList.class)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateEntryPosition(ShoppingList list, ShoppingEntry entry, int position) {
        Map<String, Object> updatePosition = new HashMap<>();
        updatePosition.put(POSITION_PROPERTY, position);
        getListsRootCollectionRef().document(list.getUid()).collection(ENTRIES_KEY).document(entry.getUid()).update(updatePosition);
    }

    /**
     * Updates if the entry is done or not.
     * During this process the done counter of the corresponding list will be updated.
     *
     * @param listId Id of the list containing the entry.
     * @param entry  The entry, with the new done-status.
     */
    @Override
    public void updateStatusDone(String listId, ShoppingEntry entry) {
        Map<String, Object> updateIsDone = new HashMap<>();
        updateIsDone.put(DONE_PROPERTY, entry.isDone());
        getListsRootCollectionRef().document(listId).collection(ENTRIES_KEY).document(entry.getUid())
                .update(updateIsDone)
                .addOnSuccessListener(aVoid -> {
                    updateListStatusCounter(listId);
                    Log.d(FIREBASE_TAG, "Success: Updated Status");
                })
                .addOnFailureListener(e -> {
                            Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                            toastMaker.prepareToast("Fail: Update Status \"Done\"");
                        }
                );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateListName(ShoppingList list) {
        Map<String, Object> updateName = new HashMap<>();
        updateName.put(NAME_PROPERTY, list.getName());
        getListsRootCollectionRef().document(list.getUid()).update(updateName)
                .addOnSuccessListener(aVoid ->
                        Log.d(FIREBASE_TAG, "Success: Updated Name")
                )
                .addOnFailureListener(e -> {
                            Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                            toastMaker.prepareToast("Fail: Update List Name");
                        }
                );
    }

    /**
     * Updates the complete entry.
     * During the process this entry will be added to history.
     *
     * @param list    List containing the entry, which is supposed to be updated.
     * @param entry   The modified entry.
     * @param context The application context.
     */
    @Override
    public void modifyWholeEntry(ShoppingList list, ShoppingEntry entry, Context context) {
        Map<String, Object> updateEntryMap = buildUpdateMap(entry);
        getListsRootCollectionRef().document(list.getUid()).collection(ENTRIES_KEY).document(entry.getUid()).update(updateEntryMap)
                .addOnSuccessListener(aVoid -> {
                    handleImageUpdate(list.getUid(), entry, context);
                    Log.d(FIREBASE_TAG, "Success: Updated Entry");
                }).addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Modify Entry");
                }
        );
        updateListStatusCounter(list.getUid());
    }


    private boolean isUploadUri(ShoppingEntry entry) {
        return entry.getImageURI() != null && !entry.getImageURI().startsWith("http");
    }


    private Map<String, Object> buildUpdateMap(ShoppingEntry entry) {
        Map<String, Object> updateEntryMap = new HashMap<>();
        updateEntryMap.put(NAME_PROPERTY, entry.getName());
        updateEntryMap.put(FirebaseSource.DONE_PROPERTY, entry.isDone());
        updateEntryMap.put(DETAILS_PROPERTY, entry.getDetails());
        updateEntryMap.put(POSITION_PROPERTY, entry.getPosition());
        updateEntryMap.put(QUANTITY_PROPERTY, entry.getQuantity());
        updateEntryMap.put(UNIT_OF_QUANTITY_PROPERTY, entry.getUnitOfQuantity());
        return updateEntryMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getHistory(Consumer<List<EntryHistoryElement>> callback) {
        Task<QuerySnapshot> querySnapshotTask = getHistoryRootCollectionRef().get();
        querySnapshotTask.addOnSuccessListener(snapshots -> {
            List<EntryHistoryElement> collectedHistory = collectHistoryAsList(snapshots);
            callback.accept(collectedHistory);
            Log.d(FIREBASE_TAG, "Success: Retrieved history");
        }).addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Retrieve History");
                }
        );
    }

    private List<EntryHistoryElement> collectHistoryAsList(QuerySnapshot snapshots) {
        return snapshots.getDocuments()
                .stream()
                .map(this::makeHistoryElement)
                .collect(toList());
    }


    private Task<QuerySnapshot> getHistory() {
        return getHistoryRootCollectionRef().get();
    }

    private EntryHistoryElement makeHistoryElement(DocumentSnapshot doc) {
        return new EntryHistoryElement((String) doc.get(NAME_PROPERTY), (String) doc.get(UNIT_OF_QUANTITY_PROPERTY),
                (String) doc.get(DETAILS_PROPERTY), (String) doc.get(IMAGE_URI_PROPERTY),
                (String) doc.get(HIST_UID_PROPERTY));
    }

    private DocumentReference buildPathForEntryDoc(String listId, DocumentSnapshot doc) {
        return getListsRootCollectionRef().document(listId).collection(ENTRIES_KEY).document(doc.getId());
    }


    private DocumentReference buildPathForHistoryDoc(DocumentSnapshot doc) {
        return getHistoryRootCollectionRef().document(doc.getId());
    }


    private void deleteListOnly(String listId) {
        DocumentReference entryRef = getListsRootCollectionRef().document(listId);
        entryRef.delete().addOnSuccessListener(aVoid ->
                Log.d(FIREBASE_TAG, "Success: Deleted List")

        ).addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Delete List");
                }
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteHistory() {
        getHistoryRootCollectionRef().get().addOnSuccessListener(documentSnapshots -> {
            documentSnapshots.getDocuments().stream()
                    .map(this::buildPathForHistoryDoc)
                    .forEach(DocumentReference::delete);
            Log.d(FIREBASE_TAG, "Success: Deleted History");
        }).addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Delete History");
                }
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAllLists() {
        Task<QuerySnapshot> querySnapshotTask = getListsRootCollectionRef().get();
        querySnapshotTask.addOnSuccessListener(aVoid -> {
            querySnapshotTask.getResult().getDocuments().stream()
                    .map(DocumentSnapshot::getId)
                    .forEach(this::deleteList);
            Log.d(FIREBASE_TAG, "Success: Deleted All Lists");
        }).addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Delete all List");
                }
        );
    }

    private StorageReference buildStorageReference() {
        return FirebaseStorage.getInstance().getReference(IMAGE_STORAGE_KEY + "/" + UUID.randomUUID());
    }

    private void updateImage(String listName, ShoppingEntry entry) {
        String imageURI = entry.getImageURI();
        Map<String, Object> updateImageMap = new HashMap<>();
        updateImageMap.put(IMAGE_URI_PROPERTY, imageURI);
        getListsRootCollectionRef().document(listName).collection(ENTRIES_KEY).document(entry.getUid()).update(updateImageMap).addOnSuccessListener(aVoid -> {
                    Log.d(FIREBASE_TAG, "Success: Updated Image");
                    ShoppingEntry entryWithImage = new ShoppingEntry(entry);
                    entryWithImage.setImageURI(imageURI);
                    addToHistory(entryWithImage);
                }
        ).addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Update Image");
                }
        );
    }

    /**
     * Uploads Image to Firebase Storage.
     * Afterwards an update of the specific ShoppingEntry is triggered.
     * Tries to compress images if possible.
     *
     * @param listName Name of the shopping list
     * @param entry    Entry which
     * @param context  Context of the image-uri
     */
    @Override
    public void uploadImage(String listName, ShoppingEntry entry, Context context) {
        Uri imageURI = Uri.parse(entry.getImageURI());
        final StorageReference image = buildStorageReference();
        byte[] compressedImageBytes = new ImageCompressorToJPEG(context).compress(imageURI, 30);
        UploadTask uploadTask;
        if (isCompressed(compressedImageBytes)) {
            uploadTask = image.putBytes(compressedImageBytes);
        } else {
            uploadTask = image.putFile(imageURI);
        }
        reactToResultOfUpload(uploadTask, image, listName, entry);
    }

    private boolean isCompressed(byte[] compressedImageBytes) {
        return compressedImageBytes != null;
    }

    private void reactToResultOfUpload(UploadTask uploadTask, StorageReference image, String listName, ShoppingEntry entry) {
        uploadTask.addOnSuccessListener(taskSnapshot -> image.getDownloadUrl()
                .addOnSuccessListener(downloadUri -> {
                    entry.setImageURI(downloadUri.toString());
                    updateImage(listName, entry);
                }))
                .addOnFailureListener(e -> {
                            Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                            toastMaker.prepareToast("Fail: Upload compressed Image");
                        }
                );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteHistoryEntry(EntryHistoryElement historyEntry) {
        String historyId = historyEntry.getUid();
        getHistoryRootCollectionRef().document(historyId).delete()
                .addOnSuccessListener(aVoid -> Log.d(FIREBASE_TAG, "Success: Deleted history-entry")).addOnFailureListener(e -> {
                    Log.d(FIREBASE_TAG, Objects.requireNonNull(e.getMessage()));
                    toastMaker.prepareToast("Fail: Delete List");
                }
        );
    }
}
