let React = require('react');

let UserToolbarItem = React.createClass({

	render: function() {
		if (!!this.props.googleUser) {
			let profile = this.props.googleUser.getBasicProfile();
			let email = profile.getEmail();
			let avatarUrl = profile.getImageUrl();

			let avatarStyle = {
				backgroundImage: (avatarUrl && avatarUrl.length) ? "url(" + avatarUrl + ")" : undefined
			};

			return (
				<div className="item user" onClick={this.onClick}>
					<div className="avatar" style={avatarStyle}></div>
					<div className="name">{email}</div>
				</div>
			)
		}
	},

	onClick: function() {
		console.log('onClick');
	}

});

module.exports = UserToolbarItem;

