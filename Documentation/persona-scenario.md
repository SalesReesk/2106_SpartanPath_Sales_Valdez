#### STUDENT / FACULTY (TEACHER)

##### Account
* Registers/Logs in with institutional email (`@g.batstate-u.edu.ph`).
* Logs out.
* Views their profile avatar, ID number, and access level.
* Updates their profile name.
* Changes their password securely via Firebase Auth.

##### Campus Navigation & Map Discovery
* Opens the interactive KML campus map.
* Views building polygons and permanent building labels.
* Selects a destination building to view its asset inventory grouped by floor and room.
* Computes A* shortest path navigation routes from current GPS position to destination.
* Views turn-by-turn walking steps and simulated GPS movement.
* Receives arrival alerts upon arriving within 2 meters of the destination.

##### Asset Discovery & Inventory
* Searches assets by ID, name, building, or room.
* Filters assets by building and availability status chips (Available, Needs Repair, Damaged).
* Sorts inventory items instantly using the quick-sort modal button (Tag A-Z, Name A-Z, Status, Condition).
* Views asset details in a modal card with automatic `ic_inventory_box` photo placeholder fallback.
* *(Students: Read-only inventory access; Teachers: Faculty read/write access).*

##### Asset & Maintenance Reporting
* Reports a damaged, missing, or faulty asset directly from equipment details.
* Incident location auto-fills with the asset's assigned location and locks as read-only.
* Captures a mandatory evidence photo via device camera or gallery, crops with `uCrop`, and uploads to Cloudinary CDN.
* Submits maintenance reports to Firebase Firestore.
* Views their submitted reports, checks ticket status, and tracks repair progress.

----------------------------------------------------------------------------------

#### ADMIN

##### Account & User Management
* Logs in with administrator credentials.
* Logs out.
* Accesses the User Management screen to search user accounts.
* Creates new user accounts with specific roles (Admin, Maintenance, Teacher, Student).
* Modifies/updates a user's assigned role.
* Deletes user accounts.

##### Asset Management & Inventory
* Adds a new equipment asset with automatically generated Asset ID (`AAMS-xxxx`).
* Edits asset information, location, remarks, status, and condition.
* Deletes an asset.
* Relocates an asset and updates spatial mapping.
* Uploads/replaces an asset photo via camera or gallery (with `uCrop` and Cloudinary).
* Filters and sorts inventory across all campus buildings.

##### Maintenance Ticketing & Dispatch
* Views all campus maintenance/problem reports.
* Reviews submitted reports, priority levels, and evidence photos.
* Assigns a maintenance staff member and sets issue priority (*Pending* ➔ *In Progress*).
* Reassigns a maintenance ticket to a different staff member.
* Updates or advances ticket status through explicit status selection (*Pending*, *In Progress*, *Resolved*, *Rejected*).
* Deletes invalid or duplicate report tickets.

----------------------------------------------------------------------------------

#### Maintenance Personnel

##### Account & Profile
* Logs in with maintenance credentials.
* Logs out.
* Updates their profile name and uploads profile picture.
* Changes their password.

##### Work Order & Maintenance Management
* Views assigned maintenance reports and work orders.
* Reviews ticket details, asset tags, and evidence photos.
* Updates work order status using the explicit status selection dialog (*Pending*, *In Progress*, *Resolved*, *Rejected* without looping).
* **Permission Restriction:** Cannot assign or reassign report tickets to other staff (reassignment is restricted strictly to Administrators).
* Updates asset condition and operational status upon resolving repairs.

----------------------------------------------------------------------------------

#### GUEST / VISITOR USER

##### Public Exploration
* Launches the app and selects "Continue as Visitor Guest".
* Explores the public interactive campus map and building locations.
* Views walking directions and pathfinding routes.
* Attempts to access restricted write actions (such as inventory management or report submission), which automatically triggers a "Sign In Required" modal prompting registration or login.

----------------------------------------------------------------------------------

#### System-Level Scenarios

##### Authentication & RBAC
* Successful login across role types (Admin, Maintenance, Teacher, Student, Visitor).
* Password reset and secure password updates.
* Role-Based Access Control (RBAC) enforcement across fragments and UI actions.

##### GPS / Location & Pathfinding
* Location permissions granted or denied.
* KML campus graph parsing into `GeoNode` and `GeoEdge` spatial adjacency lists.
* A* Shortest Path calculation using Haversine distance heuristics.
* Real-time GPS tracking, pan-to-user, and arrival alert triggers.

##### Media & Storage Pipeline
* Mandatory evidence photo capture via camera or gallery.
* Aspect-ratio constrained image cropping using `uCrop`.
* Secure unsigned media upload to Cloudinary CDN returning secure secure URLs.
* Firebase Firestore NoSQL document persistence wrapped in `BaseRepository.safeCall()` exception handling.
* ViewBinding `_binding` null safety checks and `viewLifecycleOwner` lifecycle scope protection against null pointer exceptions.
