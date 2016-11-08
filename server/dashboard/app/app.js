var React = require('react');
var ReactDOM = require('react-dom');
var $ = require("./jquery-3.1.1");

var ErrorView = require('./components/ErrorView');
var UserList = require('./components/UserList');
var UserDetail = require('./components/UserDetail');

var App = React.createClass({

	getInitialState: function() {
		return {
			users: [],
			error: null,
			selectedUser: null
		}
	},

	componentDidMount: function() {
		var self = this;
		// for now, we're loading ALL users, ignoring page parameter
		$.getJSON("http://localhost:4567/api/v1/dashboard/users")
			.done(data => {
				self.setState({
					users: data.users,
					error: null
				});
			})
			.fail(e => {
				self.setState({
					users:[],
					error: e.statusText
				});
			});
	},

	render: function() {

		var userList = this.state.error ? null : <UserList users={this.state.users} click={this.showUserDetail}/>;
		var errorView = this.state.error ? <ErrorView error={this.state.error}/> : null;
		var userDetail = this.state.selectedUser ? <UserDetail user={this.state.selectedUser} close={this.handleCloseUserDetail}/> : null;

		return (
			<div className="container">
				{userList}
				{errorView}
				{userDetail}
			</div>
		)
	},

	///////////////////////////////////////////////////////////////////

	handleCloseUserDetail: function(){
		this.setState({
			selectedUser: null
		})
	},

	showUserDetail: function(user) {
		this.setState({
			selectedUser: user
		});
	}

});

ReactDOM.render(
	<App />,
	document.getElementById('app')
);