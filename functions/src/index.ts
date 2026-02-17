import * as functions from "firebase-functions";
import admin from "firebase-admin";

// Initialize Firebase Admin SDK
admin.initializeApp();

// Firestore reference
const db = admin.firestore();
const auth = admin.auth();

/**
 * Creates a new user with specified role
 * Callable only by authenticated Admin or Employee users
 */
export const createUser = functions.https.onCall(async (data, context) => {
  // Verify authentication
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated"
    );
  }

  const { email, password, role, name } = data;
  const creatorUid = context.auth.uid;

  // Validate inputs
  if (!email || !password || !role) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required fields"
    );
  }

  // Validate role
  const validRoles = ["DRIVER", "EMPLOYEE", "ADMIN"];
  if (!validRoles.includes(role)) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid role");
  }

  try {
    // Get creator's role from Firestore
    const creatorDoc = await db.collection("users").doc(creatorUid).get();
    let creatorRole: string | undefined;

    if (creatorDoc.exists) {
      creatorRole = creatorDoc.data()?.role;
    } else {
      // Fallback: Check Firebase Auth custom claims
      const userRecord = await auth.getUser(creatorUid);
      const customClaims = userRecord.customClaims as { role?: string } | undefined;
      
      if (customClaims?.role && ["ADMIN", "EMPLOYEE"].includes(customClaims.role)) {
        creatorRole = customClaims.role;
        console.log(`Creator document not found, using role from custom claims: ${creatorRole}`);
        
        // Auto-create the missing user document
        const creatorData = {
          userId: creatorUid,
          email: userRecord.email || "",
          name: userRecord.displayName || "Unknown",
          role: creatorRole,
          managerId: "",
          vehicleIds: [],
          managedDriverIds: [],
          isActive: true,
          createdAt: Date.now(),
          lastLoginAt: 0,
        };
        await db.collection("users").doc(creatorUid).set(creatorData);
        console.log(`Auto-created missing user document for uid: ${creatorUid}`);
      } else {
        throw new functions.https.HttpsError(
          "not-found",
          "Creator user not found. Please ensure your account is properly set up."
        );
      }
    }

    // Permission checks
    if (creatorRole === "DRIVER") {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Drivers cannot create users"
      );
    }

    if (creatorRole === "EMPLOYEE" && role !== "DRIVER") {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Employees can only create drivers"
      );
    }

    // Create user in Firebase Auth
    const userRecord = await auth.createUser({
      email: email,
      password: password,
      displayName: name || email.split("@")[0],
      emailVerified: false,
      disabled: false,
    });

    // Create user document in Firestore
    const userData = {
      userId: userRecord.uid,
      email: email,
      name: name || email.split("@")[0],
      role: role,
      managerId: creatorUid, // Track who created this user
      vehicleIds: [],
      managedDriverIds: [],
      isActive: true,
      createdAt: Date.now(),
      lastLoginAt: 0,
    };

    await db.collection("users").doc(userRecord.uid).set(userData);

    // If creator is EMPLOYEE, add this driver to their managed drivers
    if (creatorRole === "EMPLOYEE" && role === "DRIVER") {
      await db.collection("users").doc(creatorUid).update({
        managedDriverIds: admin.firestore.FieldValue.arrayUnion(userRecord.uid),
      });
    }

    return {
      success: true,
      message: "User created successfully",
      userId: userRecord.uid,
      email: email,
      role: role,
    };
  } catch (error: any) {
    console.error("Error creating user:", error);

    if (error.code === "auth/email-already-exists") {
      throw new functions.https.HttpsError(
        "already-exists",
        "Email already in use"
      );
    }
    if (error.code === "auth/invalid-email") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Invalid email format"
      );
    }
    if (error.code === "auth/weak-password") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Password is too weak"
      );
    }

    throw new functions.https.HttpsError(
      "internal",
      "Failed to create user: " + error.message
    );
  }
});

/**
 * Deletes a user (Admin only)
 */
export const deleteUser = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated"
    );
  }

  const { userId } = data;
  const requesterUid = context.auth.uid;

  try {
    // Verify requester is Admin
    const requesterDoc = await db.collection("users").doc(requesterUid).get();
    if (!requesterDoc.exists || requesterDoc.data()?.role !== "ADMIN") {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Only admins can delete users"
      );
    }

    // Delete from Firebase Auth
    await auth.deleteUser(userId);

    // Delete from Firestore
    await db.collection("users").doc(userId).delete();

    return { success: true, message: "User deleted successfully" };
  } catch (error: any) {
    console.error("Error deleting user:", error);
    throw new functions.https.HttpsError(
      "internal",
      "Failed to delete user: " + error.message
    );
  }
});

/**
 * Updates user role (Admin only)
 */
export const updateUserRole = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated"
    );
  }

  const { userId, newRole } = data;
  const requesterUid = context.auth.uid;

  try {
    // Verify requester is Admin
    const requesterDoc = await db.collection("users").doc(requesterUid).get();
    if (!requesterDoc.exists || requesterDoc.data()?.role !== "ADMIN") {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Only admins can update roles"
      );
    }

    // Validate new role
    const validRoles = ["DRIVER", "EMPLOYEE", "ADMIN"];
    if (!validRoles.includes(newRole)) {
      throw new functions.https.HttpsError("invalid-argument", "Invalid role");
    }

    // Update Firestore
    await db.collection("users").doc(userId).update({ role: newRole });

    // Update custom claims in Auth (for security rules)
    await auth.setCustomUserClaims(userId, { role: newRole });

    return { success: true, message: "Role updated successfully" };
  } catch (error: any) {
    console.error("Error updating role:", error);
    throw new functions.https.HttpsError(
      "internal",
      "Failed to update role: " + error.message
    );
  }
});

/**
 * Assign driver to employee (Admin only)
 */
export const assignDriverToEmployee = functions.https.onCall(
  async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated"
      );
    }

    const { driverId, employeeId } = data;
    const requesterUid = context.auth.uid;

    try {
      // Verify requester is Admin
      const requesterDoc = await db.collection("users").doc(requesterUid).get();
      if (!requesterDoc.exists || requesterDoc.data()?.role !== "ADMIN") {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Only admins can assign drivers"
        );
      }

      // Verify driver exists and is a DRIVER
      const driverDoc = await db.collection("users").doc(driverId).get();
      if (!driverDoc.exists || driverDoc.data()?.role !== "DRIVER") {
        throw new functions.https.HttpsError("invalid-argument", "Invalid driver");
      }

      // Verify employee exists and is an EMPLOYEE
      const employeeDoc = await db.collection("users").doc(employeeId).get();
      if (!employeeDoc.exists || employeeDoc.data()?.role !== "EMPLOYEE") {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Invalid employee"
        );
      }

      // Update driver's managerId
      await db.collection("users").doc(driverId).update({ managerId: employeeId });

      // Add driver to employee's managedDriverIds
      await db.collection("users").doc(employeeId).update({
        managedDriverIds: admin.firestore.FieldValue.arrayUnion(driverId),
      });

      return { success: true, message: "Driver assigned to employee" };
    } catch (error: any) {
      console.error("Error assigning driver:", error);
      throw new functions.https.HttpsError(
        "internal",
        "Failed to assign driver: " + error.message
      );
    }
  }
);

/**
 * Migrates services from vehicleId (string) to vehicleIds (array)
 * This is a one-time migration function to update existing documents
 */
export const migrateServicesVehicleIdToVehicleIds = functions.https.onCall(
  async (data, context) => {
    // Verify authentication - Admin only
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated"
      );
    }

    const requesterUid = context.auth.uid;

    try {
      // Verify requester is Admin
      const requesterDoc = await db.collection("users").doc(requesterUid).get();
      if (!requesterDoc.exists || requesterDoc.data()?.role !== "ADMIN") {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Only admins can run this migration"
        );
      }

      // Get all services that have vehicleId but not vehicleIds
      const snapshot = await db
        .collection("services")
        .where("vehicleId", "!=", "")
        .get();

      if (snapshot.empty) {
        return {
          success: true,
          message: "No services need migration",
          migratedCount: 0,
        };
      }

      let migratedCount = 0;
      const batch = db.batch();

      snapshot.forEach((doc) => {
        const data = doc.data();
        // Only migrate if vehicleIds doesn't exist or is empty
        if (!data.vehicleIds || data.vehicleIds.length === 0) {
          const vehicleId = data.vehicleId;
          batch.update(doc.ref, {
            vehicleIds: [vehicleId], // Convert single ID to array
          });
          migratedCount++;
        }
      });

      await batch.commit();

      return {
        success: true,
        message: `Successfully migrated ${migratedCount} services`,
        migratedCount: migratedCount,
      };
    } catch (error: any) {
      console.error("Error migrating services:", error);
      throw new functions.https.HttpsError(
        "internal",
        "Failed to migrate services: " + error.message
      );
    }
  }
);
