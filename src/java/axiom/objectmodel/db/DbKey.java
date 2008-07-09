package axiom.objectmodel.db;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class DbKey implements Key {

	// the name of the prototype which defines the storage of this object.
	// this is the name of the object's prototype, or one of its ancestors.
	// If null, the object is stored in the embedded db.
	private String storageName;

	// the id that defines this key's object within the above storage space
	private String id;

	private int layer = LIVE_LAYER; 

	// lazily initialized hashcode
	private transient int hashcode = 0;

	public static final int LIVE_LAYER = 0;

	static final long serialVersionUID = 1618863960930966588L;
	

	public DbKey(DbMapping dbmap, String id, int mode) {
		this.id = id;
		this.storageName = (dbmap == null) ? null : dbmap.getStorageTypeName();
		this.layer = mode;
	}
	
	public boolean equals(Object what) {
		if (what == this) {
			return true;
		}

		if (!(what instanceof DbKey)) {
			return false;
		}

		DbKey k = (DbKey) what;
		String id = getID();
		String kid = k.getID();

		return ((layer == k.getLayer()) && (getStorageName() == k.getStorageName()) && ((id == kid) || id.equals(kid)));
	}

	public int hashCode() {
		if (hashcode == 0) {
            hashcode = (storageName == null) ? (17 + (37 * id.hashCode())) + (37 * Integer.toString(this.layer).hashCode())
                                             : (17 + (37 * storageName.hashCode()) +
                                             (+37 * id.hashCode())) + (37 * Integer.toString(this.layer).hashCode());
		}

        return hashcode;
	}

	public String toString() {
		String storageName = getStorageName();
		return (storageName == null) ? ("[" + getID() + "(" + layer + ")" + "]") : (storageName + "[" + getID() + "(" + layer + ")" + "]");
	}

	protected void writeObject(ObjectOutputStream stream) throws IOException {
		writeObjectPr(stream);
		stream.writeObject(Integer.toString(layer));
	}

	protected void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
		readObjectPr(stream);
		String mode = (String) stream.readObject();
		if (mode != null) {
			this.layer = Integer.parseInt(mode);
		}
	}

	public String getID() {
		return id;
	}
	
	public int getLayer() {
		return this.layer;
	}
	
	public Key getParentKey() {
		return null;
	}

	public String getStorageName() {
		return storageName;
	}

	public void setStorageName(String sname) {
		this.storageName = sname;
	}

	// added to facilitate calling the serialization methods on subchildren
	protected void writeObjectPr(ObjectOutputStream stream) throws IOException {
		stream.writeObject(storageName);
		stream.writeObject(id);
	}

	protected void readObjectPr(ObjectInputStream stream) 
	throws IOException, ClassNotFoundException {
		storageName = (String) stream.readObject();
		id = (String) stream.readObject();
		// if storageName is not null, set it to the interned version
		if (storageName != null) {
			storageName = storageName.intern();
		}
	}

} 