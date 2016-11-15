let React = require('react');
let ReactDOM = require('react-dom');

let ErrorView = require('./components/ErrorView');
let UserList = require('./components/UserList');
let UserDetail = require('./components/UserDetail');
let UserToolbarItem = require('./components/UserToolbarItem');
let SignOutDialog = require('./components/SignOutScreen');

let debounce = require('./util/debounce');

let App = React.createClass({

	getInitialState: function () {
		return {
			users: [],
			error: null,
			selectedUser: null,
			googleUser: null,
			googleUserAuthToken: null,
			showSignOutDialog: true
		}
	},

	componentDidMount: function () {
		// need global reference for google sign in to call onUserSignedIn/onUserSignedOut
		window.app = this;
		this.initGoogleAuthorizationClient();
	},

	render: function () {

		let signedIn = !!this.state.googleUser;
		let toolbar = (
			<div className="toolbar">
				<h2>Users</h2>
				{signedIn && <UserToolbarItem googleUser={this.state.googleUser} click={this.showUserSignOutDialog}/>}
				{signedIn && <div className="item reload" onClick={this.performLoad}>Reload</div>}
			</div>
		);

		let userList = this.state.error ? null : <UserList users={this.state.users} click={this.showUserDetail}/>;

		let errorView = this.state.error ? <ErrorView error={this.state.error}/> : null;

		let userDetail = this.state.selectedUser ?
			<UserDetail user={this.state.selectedUser} googleUserAuthToken={this.state.googleUserAuthToken} close={this.closeUserDetail}/> : null;

		let signOutDialog = this.state.showSignOutDialog ?
			<SignOutDialog close={this.closeUserSignOutDialog} signOut={this.performSignOut}/> : null;

		return (
			<div className="container">
				{toolbar}
				{userList}
				{errorView}
				{userDetail}
				{signOutDialog}
			</div>
		)
	},

	///////////////////////////////////////////////////////////////////

	initGoogleAuthorizationClient: function() {
		console.log('initGoogleAuthorizationClient');
		let self = this;

		function signinChanged(signedIn) {
			console.log('signinChanged signedIn: ', signedIn);
			if (!signedIn) {
				self.onUserSignedOut();
			}
		}

		function userChanged(user) {
			console.log('userChanged: User: ', user, ' signedIn: ', user.isSignedIn());
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
				client_id: '246785936717-tfn5b0s186fuig7eo0dc826urohj1hh1.apps.googleusercontent.com',
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

	performSignOut: function() {
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

	_setSignedInState: function(signedIn) {

		// we're using a debouncer because the gapi.auth2 callbacks can trigger rapidly
		if (this._setSignedInStateDebouncer == null) {
			this._setSignedInStateDebouncer = debounce(500, function(signedIn){
				console.log('_setSignedInStateDebouncer signedIn: ', signedIn);
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

			let headers = new Headers();
			headers.set("Authorization", this.state.googleUserAuthToken);

			fetch("http://localhost:4567/api/v1/dashboard/users", {
					credentials: 'include',
					headers: headers
				})
				.then(function (response) {
					return response.json()
				})
				.then(data => {
					this.setState({
						users: data.users,
						error: null
					});
				})
				.catch(e => {
					this.setState({
						users: [],
						error: e.statusText
					});
				});
		} else {
			this.setState({
				users: [],
				error: "Unauthorized"
			})
		}
	},

	closeUserDetail: function () {
		this.setState({
			selectedUser: null
		})
	},

	showUserDetail: function (user) {
		this.setState({
			selectedUser: user
		});
	},

	showUserSignOutDialog: function() {
		this.setState({
			showSignOutDialog: true
		});
	},

	closeUserSignOutDialog: function() {
		this.setState({
			showSignOutDialog: false
		});
	}

});

ReactDOM.render(
	<App />,
	document.getElementById('app')
);