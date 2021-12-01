import React, { useEffect } from 'react';

const SubscriptionsBar = ({ setSelectedTag, subscriptionsTags }) => {
  const handleClick = (event) => {
    event.preventDefault();
    setSelectedTag(event.target.value);
  }

  useEffect(() => setSelectedTag(subscriptionsTags[0]?.name || ''), [setSelectedTag, subscriptionsTags])

	return (
		<nav className="my-4">
			{subscriptionsTags && subscriptionsTags.map((tag) => (
				<button className="mr-2" key={tag.name} onClick={handleClick} value={tag.name}>{tag.name}</button>
			))}
		</nav>
	);
};

export default SubscriptionsBar;
