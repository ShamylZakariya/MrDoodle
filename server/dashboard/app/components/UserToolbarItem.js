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
				<div className="item user" onClick={this.props.click}>
					<div className="avatar" style={avatarStyle}></div>
					<div className="name">{email}</div>
				</div>
			)
		}
	}

});

module.exports = UserToolbarItem;

