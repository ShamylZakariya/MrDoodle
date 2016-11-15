let React = require('react');
let ReactDOM = require('react-dom');

let ErrorView = require('./components/ErrorView');
let UserList = require('./components/UserList');
let UserDetail = require('./components/UserDetail');

let App = React.createClass({

	getInitialState: function () {
		return {
			users: [],
			error: null,
			selectedUser: null,
			googleUser: null,
			googleUserAuthToken: null
		}
	},

	componentDidMount: function () {
		// need global reference for google sign in to call onUserSignedIn/onUserSignedOut
		window.app = this;
	},

	render: function () {

		let signedIn = !!this.state.googleUser;
		let toolbar = (
			<div className="toolbar">
				<h2>Users</h2>
				{signedIn && <div className="item reload" onClick={this.performLoad}>Reload</div>}
			</div>
		);

		let userList = this.state.error ? null : <UserList users={this.state.users} click={this.showUserDetail}/>;
		let errorView = this.state.error ? <ErrorView error={this.state.error}/> : null;
		let userDetail = this.state.selectedUser ?
			<UserDetail user={this.state.selectedUser} googleUserAuthToken={this.state.googleUserAuthToken} close={this.handleCloseUserDetail}/> : null;

		return (
			<div className="container">
				{toolbar}
				{userList}
				{errorView}
				{userDetail}
			</div>
		)
	},

	///////////////////////////////////////////////////////////////////

	/**
	 * Called from index.html when google sign in button triggers successful sign in
	 * @param googleUser
	 */
	onUserSignedIn: function (googleUser) {

		let profile = googleUser.getBasicProfile();
		console.log('onUserSignedIn id: ' + profile.getId() + ' name: ' + profile.getName() + ' email: ' + profile.getEmail());

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
		this.setState({
			users: [],
			googleUser: null,
			googleUserAuthToken: null
		});

		this.performLoad();
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

	handleCloseUserDetail: function () {
		this.setState({
			selectedUser: null
		})
	},

	showUserDetail: function (user) {
		this.setState({
			selectedUser: user
		});
	}

});

ReactDOM.render(
	<App />,
	document.getElementById('app')
);