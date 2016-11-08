var React = require('react');
var moment = require('moment');

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
		var lastAccessDate = (new Date(user.lastAccessTimestamp * 1000));
		var formattedLastAccessDate = moment(lastAccessDate).format('MMMM Do YYYY, h:mm:ss a');

		return (
			<div className="userDetail">
				<div className="window">
					<a className="close" onClick={this.handleClose}>Close</a>

					<div className="userInfo">

						<div className="avatar"><img src={user.avatarUrl}/></div>
						<div className="email">{user.email}</div>
						<div className="id">{user.id}</div>
						<div className="lastAccessDate">{formattedLastAccessDate}</div>

					</div>
				</div>
			</div>
		)
	},

	handleClose: function() {
		this.props.close();
	}

});

module.exports = UserDetail;