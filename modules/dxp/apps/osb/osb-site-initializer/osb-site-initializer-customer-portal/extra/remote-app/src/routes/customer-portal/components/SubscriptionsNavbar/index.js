import {useQuery} from '@apollo/client';
import ClayButton from '@clayui/button';
import {DropDown} from '@clayui/core';
import ClayIcon from '@clayui/icon';
import React, {useEffect, useState} from 'react';
import BaseButton from '../../../../common/components/BaseButton';
import {getAccountSubscriptionGroups} from '../../../../common/services/liferay/graphql/queries';

const SubscriptionsNavbar = ({
	accountKey,
	selectedSubscriptionGroup,
	setSelectedSubscriptionGroup,
}) => {
	const [active, setActive] = useState(false);

	const [subscriptionsTags, setSubscriptionsTags] = useState([]);

	const {
		data: accountSubscriptions,
		loading: isAccountSubscriptionsLoading,
	} = useQuery(getAccountSubscriptionGroups, {
		variables: {
			filter: `accountKey eq '${accountKey}' and hasActivation eq true`,
		},
	});

	useEffect(() => {
		if (accountSubscriptions) {
			const accountSubsciptionsItems =
				accountSubscriptions?.c?.accountSubscriptionGroups?.items || [];

			setSubscriptionsTags(accountSubsciptionsItems);

			if (accountSubsciptionsItems.length) {
				setSelectedSubscriptionGroup(accountSubsciptionsItems[0]?.name);
			}
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [accountSubscriptions, subscriptionsTags]);

	return (
		<>
			{!isAccountSubscriptionsLoading && (
				<nav className="bg-neutral-1 border border-secondary my-4 p-1 rounded-pill">
					{subscriptionsTags &&
						subscriptionsTags.map((tag) => (
							<BaseButton
								className="btn btn-outline-primary mx-auto py-1 rounded-pill"
								key={tag.name}
								onClick={(event) =>
									setSelectedSubscriptionGroup(
										event.target.value
									)
								}
								value={tag.name}
							>
								{tag.name}
							</BaseButton>
						))}
				</nav>
			)}

			<DropDown
				active={active}
				className="my-4"
				onActiveChange={setActive}
				trigger={
					<ClayButton className="btn btn-secondary text-brand-primary-darken-5">
						{selectedSubscriptionGroup}

						<ClayIcon className="my-auto" symbol="caret-bottom" />
					</ClayButton>
				}
			>
				{subscriptionsTags &&
					subscriptionsTags.map((tag) => (
						<DropDown.Item
							key={tag.name}
							onClick={(event) => {
								event.preventDefault();
								window.alert(tag.name);
							}}
							symbolRight={
								selectedSubscriptionGroup === tag.name
									? 'check'
									: ''
							}
							value={tag.name}
						>
							{tag.name}
						</DropDown.Item>
					))}
			</DropDown>
		</>
	);
};

export default SubscriptionsNavbar;
