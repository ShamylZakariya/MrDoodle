let React = require('react');
let ReactDOM = require('react-dom');

let ErrorDialog = require('./components/ErrorDialog');
let UserList = require('./components/UserList');
let UserDialog = require('./components/UserDialog');
let UserToolbarItem = require('./components/UserToolbarItem');
let SignOutDialog = require('./components/SignOutScreen');
let UserStatusBar = require('./components/UserStatusBar');

let debounce = require('./util/debounce');
let googleClientId = require('./config/google_client_id');

let App = React.createClass({

	getInitialState: function () {
		return {
			users: [],
			totalUsers: 0,
			totalConnectedUsers: 0,
			error: null,
			selectedUser: null,
			googleUser: null,
			googleUserAuthToken: null,
			showSignOutDialog: false
		}
	},

	componentDidMount: function () {
		this.initGoogleAuthorizationClient();
	},

	render: function () {

		let signedIn = !!this.state.googleUser;
		let toolbar = (
			<div className="toolbar">
				<div className="content">
					<h2>Users</h2>
					{signedIn &&
					<UserToolbarItem googleUser={this.state.googleUser} click={this.showUserSignOutDialog}/>}
					{signedIn && <div className="item reload" onClick={this.performLoad}>Reload</div>}
				</div>
			</div>
		);

		let userList = <UserList users={this.state.users} click={this.showUserDialog}/>;

		let statusBar =
			<UserStatusBar totalUsers={this.state.totalUsers} totalConnectedUsers={this.state.totalConnectedUsers}/>

		let errorDialog = this.state.error ?
			<ErrorDialog error={this.state.error} close={this.closeErrorDialog}/> : null;

		let userDialog = this.state.selectedUser ?
			<UserDialog user={this.state.selectedUser} googleUserAuthToken={this.state.googleUserAuthToken} close={this.closeUserDialog}/> : null;

		let signOutDialog = this.state.showSignOutDialog ?
			<SignOutDialog close={this.closeUserSignOutDialog} signOut={this.performSignOut}/> : null;

		return (
			<div className="container">
				{toolbar}
				<div className="scrollview">
					{userList}
				</div>
				{statusBar}
				{errorDialog}
				{userDialog}
				{signOutDialog}
			</div>
		)
	},

	///////////////////////////////////////////////////////////////////

	initGoogleAuthorizationClient: function () {
		console.log('initGoogleAuthorizationClient');
		let self = this;

		function signinChanged(signedIn) {
			if (!signedIn) {
				self.onUserSignedOut();
			}
		}

		function userChanged(user) {
			if (user.isSignedIn()) {
				self.onUserSignedIn(user);
			} else {
				self.onUserSignedOut();
			}
		}

		function signInButtonSuccess(user) {
			console.log('signInButtonSuccess user: ' + user.getBasicProfile().getName());
		}

		function signInButtonFailure(e) {
			console.error("signInButtonFailure: ", e);
		}

		gapi.load('auth2', () => {
			/**
			 * Retrieve the singleton for the GoogleAuth library and set up the
			 * client.
			 */
			this.auth2 = gapi.auth2.init({
				client_id: googleClientId,
				scope: "profile email"
			});

			// Attach the click handler to the sign-in button
			this.auth2.attachClickHandler('signInButton', {}, signInButtonSuccess, signInButtonFailure);

			// Listen for sign-in state changes.
			this.auth2.isSignedIn.listen(signinChanged);

			// Listen for changes to current user.
			this.auth2.currentUser.listen(userChanged);

			// Sign in the user if they are currently signed in.
			if (this.auth2.isSignedIn.get() == true) {
				this.auth2.signIn();
			}

			userChanged(this.auth2.currentUser.get());
		});

	},

	performSignOut: function () {
		gapi.auth2.getAuthInstance().signOut().then(() => {
			this.onUserSignedOut();
		});
	},

	/**
	 * Called from index.html when google sign in button triggers successful sign in
	 * @param googleUser
	 */
	onUserSignedIn: function (googleUser) {
		let profile = googleUser.getBasicProfile();
		console.log('onUserSignedIn id: ' + profile.getId() + ' name: ' + profile.getName() + ' email: ' + profile.getEmail());

		// change body signin/out state
		this._setSignedInState(true);

		this.setState({
			googleUser: googleUser,
			googleUserAuthToken: googleUser.getAuthResponse().id_token
		});

		this.performLoad();
	},

	/**
	 * Called from index.html on signing out from google id service
	 */
	onUserSignedOut: function () {
		console.log('onUserSignedOut');

		this._setSignedInState(false);
		this.setState({
			users: [],
			googleUser: null,
			googleUserAuthToken: null
		});

		this.performLoad();
	},

	_setSignedInState: function (signedIn) {

		// we're using a debouncer because the gapi.auth2 callbacks can trigger rapidly
		if (this._setSignedInStateDebouncer == null) {
			this._setSignedInStateDebouncer = debounce(500, function (signedIn) {
				if (signedIn) {
					document.body.classList.remove("signedOut");
					document.body.classList.add("signedIn");
				} else {
					document.body.classList.add("signedOut");
					document.body.classList.remove("signedIn");
				}
			});
		}

		this._setSignedInStateDebouncer(signedIn);
	},

	performLoad: function () {
		let authToken = this.state.googleUserAuthToken;
		if (!!authToken) {

			this.setState({
				error: null
			});

			this._loadUserList();
			this._loadUserStatus();

		} else {
			this.setState({
				users: [],
				error: "Unauthorized - Please sign in."
			})
		}
	},

	_loadUserList: function () {
		let headers = new Headers();
		headers.set("Authorization", this.state.googleUserAuthToken);

		fetch("http://localhost:4567/api/v1/dashboard/users", {
			credentials: 'include',
			headers: headers
		})
			.then(function (response) {
				if (response.ok) {
					return response.json()
				} else {
					throw new Error(response.status + " : " + response.statusText);
				}
			})
			.then(data => {
				this.setState({
					users: data.users
				});
			})
			.catch(e => {
				this.setState({
					users: [],
					error: e.message
				});
			});
	},

	_loadUserStatus: function () {
		let headers = new Headers();
		headers.set("Authorization", this.state.googleUserAuthToken);

		fetch("http://localhost:4567/api/v1/dashboard/userStatus", {
			credentials: 'include',
			headers: headers
		})
			.then(function (response) {
				if (response.ok) {
					return response.json()
				} else {
					throw new Error(response.status + " : " + response.statusText);
				}
			})
			.then(data => {
				console.log('_loadUserStatus: data: ', data);
				this.setState({
					totalUsers: data.totalUsers,
					totalConnectedUsers: data.totalConnectedUsers
				});
			})
			.catch(e => {
				this.setState({
					totalUsers: 'N/A',
					totalConnectedUsers: 'N/A',
					error: e.message
				});
			});
	},


	closeUserDialog: function () {
		this.setState({
			selectedUser: null
		})
	},

	showUserDialog: function (user) {
		this.setState({
			selectedUser: user
		});
	},

	showUserSignOutDialog: function () {
		this.setState({
			showSignOutDialog: true
		});
	},

	closeUserSignOutDialog: function () {
		this.setState({
			showSignOutDialog: false
		});
	},

	closeErrorDialog: function () {
		this.setState({
			error: null
		})
	}

});

ReactDOM.render(
	<App />,
	document.getElementById('app')
);