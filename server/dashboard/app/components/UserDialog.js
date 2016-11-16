let React = require('react');
let moment = require('moment');

let UserDialog = React.createClass({

	getDefaultProps: function () {
		return {
			user: null
		}
	},

	getInitialState: function () {
		return {
			connectedDeviceCount: 0,
			user: null,
			error: null
		}
	},

	componentDidMount: function () {
		// perform a $.getJSON call to get the details on our user
		this.loadUserInfo();
		this.updateUserInfoInterval = setInterval(this.loadUserInfo, 2000);
	},

	componentWillUnmount: function () {
		if (this.updateUserInfoInterval) {
			clearInterval(this.updateUserInfoInterval);
		}
	},

	render: function () {

		let user = this.state.user;

		if (user == null) {
			return <div className="loading">Loading...</div>
		}


		let lastAccessDate = (new Date(user.lastAccessTimestampSeconds * 1000));
		let formattedLastAccessDate = moment(lastAccessDate).format('MMMM Do YYYY, h:mm:ss a');

		let styles = {
			avatarImageStyle: {
				backgroundImage: (user.avatarUrl && user.avatarUrl.length) ? "url(" + user.avatarUrl + ")" : undefined
			}
		};

		let connectedMarkerClassName = "connectedMarker " + (this.state.connectedDeviceCount > 0 ? "connected" : "disconnected");

		return (
			<div className="userDetail modal">
				<div className="window">
					<a className="close" onClick={this.handleClose}>Close</a>

					<div className="content userInfo">

						<div className="avatar">
							<div className="avatarImage" style={styles.avatarImageStyle}></div>
							<div className={connectedMarkerClassName}>
								<span className="count">{this.state.connectedDeviceCount}</span></div>
						</div>
						<div className="email">{user.email}</div>
						<div className="id">{user.accountId}</div>
						<div className="lastAccessDate">{formattedLastAccessDate}</div>

					</div>
				</div>
			</div>
		)
	},

	loadUserInfo: function () {

		let googleUserAuthToken = this.props.googleUserAuthToken;
		if (!!googleUserAuthToken) {

			let headers = new Headers();
			headers.set("Authorization", this.props.googleUserAuthToken);

			let url = "http://localhost:4567/api/v1/dashboard/users/" + this.props.user.accountId;
			fetch(url, {
				credentials: 'include',
				headers: headers
			})
				.then(response => {
					return response.json()
				})
				.then(data => {
					if (this.isMounted()) {
						this.setState({
							connectedDeviceCount: data.connectedDevices,
							user: data.user,
						});
					}
				})
				.catch(e => {
					if (this.isMounted()) {
						this.setState({
							connectedDeviceCount: 0,
							error: e.statusText
						});
					}
				});
		} else {
			this.setState({
				connectedDeviceCount: 0,
				error: "Unauthorized"
			});
		}
	},

	handleClose: function () {
		this.props.close();
	}

});

module.exports = UserDialog;