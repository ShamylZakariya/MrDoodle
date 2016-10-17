#Currently

Design a Dashboard API

api/v1/dashboard/status
	- # connected users
	- # connected devices for each user

api/v1/dashboard/users
	- list of userIDs

api/v1/dashboard/user/{userId}/blobs
api/v1/dashboard/user/{userId}/blobs/{blobId}
api/v1/dashboard/user/{userId}/timestamps


#Considerations
Do we need read/write lock in server