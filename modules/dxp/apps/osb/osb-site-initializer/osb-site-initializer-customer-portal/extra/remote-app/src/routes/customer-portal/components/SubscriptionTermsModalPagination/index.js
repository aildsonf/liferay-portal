import {ClayPaginationBarWithBasicItems} from '@clayui/pagination-bar';
import React from 'react';

const SubscriptionTermsModalPagination = ({
	activePage,
	setActivePage,
	totalItems,
}) => {
	const handlePageChange = (page) => {
		setActivePage(page);
	};

	return (
		<div className="mb-3 mx-3">
			<ClayPaginationBarWithBasicItems
				activeDelta={5}
				activePage={activePage}
				ellipsisBuffer={3}
				onPageChange={handlePageChange}
				showDeltasDropDown={false}
				spritemap={`${Liferay.ThemeDisplay.getPathThemeImages()}/clay/icons.svg`}
				totalItems={totalItems}
			/>
		</div>
	);
};

export default SubscriptionTermsModalPagination;
