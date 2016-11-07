var React = require('react');

var UserDetail = React.createClass({

	getDefaultProps: function() {
		return {
			user: null
		}
	},

	getInitialState: function() {
		return {

		}
	},

	componentDidMount: function(){
		// perform a $.getJSON call to get the details on our user
	},

	render: function(){
		var user = this.props.user;
		var avatar = !!user.avatarUrl ? <li className="avatar">{user.avatarUrl}</li> : null;

		return (
			<div className="userDetail">
				<a className="close" onClick={this.handleClose}>Close</a>

				<ul className="userInfo">

					{avatar}
					<li className="email">{user.email}</li>
					<li className="id">{user.id}</li>
					<li className="lastAccessTimestamp">{user.lastAccessTimestamp}</li>

				</ul>
			</div>
		)
	},

	handleClose: function() {
		this.props.close();
	}

});

module.exports = UserDetail;